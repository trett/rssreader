package ru.trett.rss.server.services

import cats.effect.IO
import cats.syntax.all.*
import io.circe.generic.semiauto.*
import io.circe.{Decoder, Encoder}
import org.http4s.MediaType
import org.http4s.circe.CirceEntityDecoder.*
import org.http4s.circe.CirceEntityEncoder.*
import org.http4s.client.Client
import org.http4s.headers.`Content-Type`
import org.http4s.{Method, Request, Uri}
import org.typelevel.log4cats.LoggerFactory
import retry.*
import retry.RetryPolicies.*
import ru.trett.rss.server.models.{Feed, User}

import scala.concurrent.duration.*

private case class GeminiPart(text: String)
private case class GeminiContent(parts: List[GeminiPart])
private case class GeminiRequest(contents: List[GeminiContent])
private case class GeminiResponsePart(text: String)
private case class GeminiResponseContent(parts: List[GeminiResponsePart])
private case class GeminiCandidate(content: GeminiResponseContent)
private case class GeminiResponse(candidates: List[GeminiCandidate])

private given Encoder[GeminiPart] = deriveEncoder
private given Encoder[GeminiContent] = deriveEncoder
private given Encoder[GeminiRequest] = deriveEncoder
private given Decoder[GeminiResponsePart] = deriveDecoder
private given Decoder[GeminiResponseContent] = deriveDecoder
private given Decoder[GeminiCandidate] = deriveDecoder
private given Decoder[GeminiResponse] = deriveDecoder

class ImportanceService(client: Client[IO])(using loggerFactory: LoggerFactory[IO]):

    private val logger = loggerFactory.getLogger
    private val batchSize = 10

    private val retryPolicy =
        limitRetries[IO](3) |+| exponentialBackoff[IO](1.second)

    def score(user: User, highlightedChannelIds: Set[Long], feeds: List[Feed]): IO[List[Feed]] =
        if feeds.isEmpty then IO.pure(Nil)
        else
            val (preDecided, needsAI) = feeds.partition(f =>
                highlightedChannelIds.contains(f.channelId) ||
                    matchesKeywordRule(user.settings.keywordRules, f)
            )
            val decidedFeeds = preDecided.map(_.copy(important = true))
            user.settings.geminiApiKey.filter(_.nonEmpty) match
                case None =>
                    // No AI key: keyword/highlighted pre-decisions only; rest unchanged
                    logger.info(
                        s"[Importance] ${feeds.size} feeds for ${user.email}: ${preDecided.size} pre-decided (no AI key)"
                    ) *> IO.pure {
                        val resultMap = decidedFeeds.map(f => (f.link, f.userId) -> f).toMap
                        feeds.map(f => resultMap.getOrElse((f.link, f.userId), f))
                    }
                case Some(apiKey) =>
                    val bannedCategories = user.settings.bannedCategories
                    logger.info(
                        s"[Importance] ${feeds.size} feeds for ${user.email}: ${preDecided.size} pre-decided, ${needsAI.size} → Gemini"
                    ) *> {
                        val aiIO = needsAI
                            .groupBy(_.channelId)
                            .values
                            .toList
                            .flatTraverse(channelFeeds =>
                                channelFeeds
                                    .grouped(batchSize)
                                    .toList
                                    .flatTraverse(classifyBatch(_, apiKey, bannedCategories))
                            )
                        aiIO.map { aiFeeds =>
                            val resultMap =
                                (decidedFeeds ++ aiFeeds).map(f => (f.link, f.userId) -> f).toMap
                            feeds.map(f => resultMap.getOrElse((f.link, f.userId), f))
                        }
                    }

    private def matchesKeywordRule(rules: List[String], feed: Feed): Boolean =
        rules.exists { kw =>
            feed.title.toLowerCase.contains(kw.toLowerCase) ||
            feed.categories.exists(_.toLowerCase == kw.toLowerCase)
        }

    private def classifyBatch(
        batch: List[Feed],
        apiKey: String,
        bannedCategories: List[String]
    ): IO[List[Feed]] =
        if batch.isEmpty then IO.pure(Nil)
        else
            val prompt = buildBatchPrompt(batch, bannedCategories)
            logger.info(
                s"[Importance] Sending batch of ${batch.size} items to Gemini (channelId=${batch.head.channelId})"
            ) *>
                retryingOnSomeErrors(
                    policy = retryPolicy,
                    isWorthRetrying = (e: Throwable) => IO.pure(isRetryable(e)),
                    onError = (e: Throwable, details: RetryDetails) =>
                        logger.warn(
                            s"[Importance] Gemini batch call failed ($details): ${e.getMessage}"
                        )
                )(callGeminiBatch(prompt, batch, apiKey, bannedCategories))
                    .handleErrorWith { e =>
                        logger.warn(
                            s"[Importance] Gemini batch permanently failed — defaulting to not-important: ${e.getMessage}"
                        ) *> IO.pure(batch.map(_.copy(important = false, isRead = true)))
                    }

    private val maxDescriptionLength = 500

    private def buildBatchPrompt(batch: List[Feed], bannedCategories: List[String]): String =
        val items = batch.zipWithIndex
            .map { case (f, i) =>
                val desc =
                    if f.description.length > maxDescriptionLength
                    then f.description.take(maxDescriptionLength) + "…"
                    else f.description
                s"${i + 1}. Title: \"${f.title}\" | Description: \"$desc\" | Categories: \"${f.categories.mkString(", ")}\""
            }
            .mkString("\n")
        val bannedNote =
            if bannedCategories.nonEmpty then
                s"""
                   |
                   |Always answer "no" for any item whose title, description, or categories are related to: ${bannedCategories
                      .mkString(", ")}.
                   |""".stripMargin
            else ""
        s"""Classify each RSS item as important or not. An item is important if it is a significant announcement, security vulnerability, major software release, or breaking news — judged by the standards of the article's own language and cultural context. Articles may be in any language; classify them accordingly.$bannedNote
           |Reply with exactly one word per line — "yes" or "no" — in the same order as the items. No other text.
           |
           |Items:
           |$items""".stripMargin

    private def isInBannedCategory(feed: Feed, bannedCategories: List[String]): Boolean =
        bannedCategories.nonEmpty &&
            feed.categories.exists(c => bannedCategories.exists(_.equalsIgnoreCase(c)))

    private def callGeminiBatch(
        prompt: String,
        batch: List[Feed],
        apiKey: String,
        bannedCategories: List[String]
    ): IO[List[Feed]] =
        for
            _ <- logger.info("[Importance] Calling Gemini API")
            uri <- IO.fromEither(
                Uri.fromString(
                    s"https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-lite:generateContent?key=$apiKey"
                )
            )
            requestBody = GeminiRequest(List(GeminiContent(List(GeminiPart(prompt)))))
            request = Request[IO](Method.POST, uri)
                .withEntity(requestBody)
                .withHeaders(`Content-Type`(MediaType.application.json))
            response <- client.expect[GeminiResponse](request)
            rawText = response.candidates
                .flatMap(_.content.parts)
                .headOption
                .map(_.text.trim)
                .getOrElse("")
            answers = rawText.linesIterator.map(_.trim.toLowerCase).filter(_.nonEmpty).toList
            result <-
                if answers.size != batch.size then
                    logger
                        .warn(
                            s"[Importance] Gemini returned ${answers.size} answers for ${batch.size} items — defaulting all to not-important"
                        )
                        .as(batch.map(_.copy(important = false, isRead = true)))
                else
                    IO.pure(batch.zip(answers).map { case (feed, answer) =>
                        val imp =
                            answer.startsWith("yes") && !isInBannedCategory(feed, bannedCategories)
                        feed.copy(important = imp, isRead = !imp)
                    })
        yield result

    private def isRetryable(e: Throwable): Boolean =
        Option(e.getMessage).exists(msg =>
            msg.contains("429") || msg.contains("500") || msg.contains("503")
        )
