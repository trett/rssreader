package ru.trett.rss.server.services

import cats.effect.IO
import cats.syntax.all.*
import io.circe.generic.semiauto.*
import io.circe.syntax.*
import io.circe.{Decoder, Encoder}
import org.http4s.circe.CirceEntityDecoder.*
import org.http4s.client.Client
import org.http4s.{Method, Request, Uri}
import org.http4s.headers.`Content-Type`
import org.http4s.MediaType
import org.typelevel.log4cats.LoggerFactory
import retry.RetryPolicies.*
import retry.*
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

class ImportanceService(client: Client[IO], apiKey: String)(using loggerFactory: LoggerFactory[IO]):

    private val logger = loggerFactory.getLogger

    private val retryPolicy =
        limitRetries[IO](3) |+| exponentialBackoff[IO](1.second)

    def score(user: User, highlightedChannelIds: Set[Long], feeds: List[Feed]): IO[List[Feed]] =
        if apiKey.isEmpty then IO.pure(feeds)
        else feeds.traverse(scoreFeed(user, highlightedChannelIds))

    private def scoreFeed(user: User, highlightedChannelIds: Set[Long])(feed: Feed): IO[Feed] =
        if highlightedChannelIds.contains(feed.channelId) then IO.pure(feed.copy(important = true))
        else if matchesKeywordRule(user.settings.keywordRules, feed) then
            IO.pure(feed.copy(important = true))
        else classifyWithGemini(feed).map(imp => feed.copy(important = imp))

    private def matchesKeywordRule(rules: List[String], feed: Feed): Boolean =
        rules.exists { kw =>
            feed.title.toLowerCase.contains(kw.toLowerCase) ||
            feed.categories.exists(_.toLowerCase == kw.toLowerCase)
        }

    private def classifyWithGemini(feed: Feed): IO[Boolean] =
        val prompt =
            s"""Is this RSS item a significant tech announcement, security vulnerability, major release, or breaking industry news?
               |Title: ${feed.title}
               |Categories: ${feed.categories.mkString(", ")}
               |Reply with exactly one word: yes or no.""".stripMargin

        retryingOnSomeErrors(
            policy = retryPolicy,
            isWorthRetrying = (e: Throwable) => IO.pure(isRetryable(e)),
            onError = (e: Throwable, details: RetryDetails) =>
                logger.warn(s"Gemini call failed ($details): ${e.getMessage}")
        )(callGemini(prompt)).handleError { _ => false }

    private def isRetryable(e: Throwable): Boolean =
        Option(e.getMessage).exists(msg =>
            msg.contains("429") || msg.contains("500") || msg.contains("503")
        )

    private def callGemini(prompt: String): IO[Boolean] =
        for
            uri <- IO.fromEither(
                Uri.fromString(
                    s"https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash-lite:generateContent?key=$apiKey"
                )
            )
            requestBody = GeminiRequest(List(GeminiContent(List(GeminiPart(prompt)))))
            request = Request[IO](Method.POST, uri)
                .withEntity(requestBody.asJson.noSpaces)
                .withHeaders(`Content-Type`(MediaType.application.json))
            response <- client.expect[GeminiResponse](request)
            text = response.candidates
                .flatMap(_.content.parts)
                .headOption
                .map(_.text.trim.toLowerCase)
                .getOrElse("")
        yield text.startsWith("yes")
