package ru.trett.rss.server.services

import cats.effect.IO
import fs2.Stream
import io.circe.Decoder
import io.circe.Json
import io.circe.generic.auto.*
import org.http4s.Header
import org.http4s.Headers
import org.http4s.Method
import org.http4s.Request
import org.http4s.Uri
import org.http4s.circe.CirceEntityCodec.*
import org.http4s.client.Client

import org.typelevel.ci.*
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.LoggerFactory
import ru.trett.rss.models.{SummaryLanguage, SummaryModel, SummaryEvent}
import ru.trett.rss.server.models.User
import ru.trett.rss.server.repositories.FeedRepository
import org.jsoup.Jsoup
import java.util.concurrent.TimeoutException

case class Part(text: String)
case class Content(parts: Option[List[Part]])
case class Candidate(content: Content)
case class GeminiResponse(candidates: List[Candidate])

class SummarizeService(feedRepository: FeedRepository, client: Client[IO], apiKey: String)(using
    loggerFactory: LoggerFactory[IO]
):

    given Decoder[GeminiResponse] = Decoder.forProduct1("candidates")(GeminiResponse.apply)
    private val logger: Logger[IO] = LoggerFactory[IO].getLogger
    private val batchSize = 30

    private def getEndpoint(modelId: String, stream: Boolean = false): Uri =
        val method = if stream then "streamGenerateContent" else "generateContent"
        Uri.unsafeFromString(
            s"https://generativelanguage.googleapis.com/v1beta/models/$modelId:$method"
        )

    private def buildGeminiRequest(
        modelId: String,
        prompt: String,
        temperature: Option[Double] = None,
        stream: Boolean = false
    ): IO[Request[IO]] =
        val baseConfig = Json.obj(
            "contents" -> Json
                .arr(Json.obj("parts" -> Json.arr(Json.obj("text" -> Json.fromString(prompt)))))
        )

        // Build model-specific thinking configuration
        val thinkingConfig =
            if SummaryModel.usesThinkingLevel(modelId) then
                Json.obj("thinkingLevel" -> Json.fromString("low"))
            else if SummaryModel.usesThinkingBudget(modelId) then
                Json.obj("thinkingBudget" -> Json.fromInt(1024))
            else Json.obj() // No thinking config for unknown models

        // Build generation config with thinking config and optional temperature
        val generationConfig = temperature match
            case Some(temp) =>
                Json.obj(
                    "thinkingConfig" -> thinkingConfig,
                    "temperature" -> Json.fromDoubleOrNull(temp)
                )
            case None =>
                Json.obj("thinkingConfig" -> thinkingConfig)

        val config = baseConfig.mapObject(_.add("generationConfig", generationConfig))

        IO.pure(
            Request[IO](
                method = Method.POST,
                uri = getEndpoint(modelId, stream),
                headers = Headers(
                    Header.Raw(ci"X-goog-api-key", apiKey),
                    Header.Raw(ci"Content-Type", "application/json")
                )
            ).withEntity(config)
        )

    def streamSummary(user: User, offset: Int): Stream[IO, SummaryEvent] =
        val selectedModel = user.settings.summaryModel
            .flatMap(SummaryModel.fromString)
            .getOrElse(SummaryModel.default)

        Stream
            .eval(feedRepository.getTotalUnreadCount(user.id))
            .flatMap { totalUnread =>
                Stream.eval(feedRepository.getUnreadFeeds(user, batchSize, offset)).flatMap {
                    feeds =>
                        val remainingAfterThis = totalUnread - offset - feeds.size
                        val metadata = SummaryEvent.Metadata(
                            feedsProcessed = feeds.size,
                            totalRemaining = Math.max(0, remainingAfterThis),
                            hasMore = remainingAfterThis > 0
                        )

                        Stream.emit(metadata) ++ (
                            if feeds.isEmpty && offset == 0 then
                                Stream
                                    .eval(generateFunFact(user, selectedModel.modelId))
                                    .map(SummaryEvent.FunFact(_)) ++ Stream.emit(SummaryEvent.Done)
                            else if feeds.isEmpty then Stream.emit(SummaryEvent.Done)
                            else
                                val text = feeds.map(_.description).mkString("\n")
                                val strippedText = Jsoup.parse(text).text()
                                val validatedLanguage = user.settings.summaryLanguage
                                    .flatMap(SummaryLanguage.fromString)
                                    .getOrElse(SummaryLanguage.English)

                                Stream
                                    .eval(
                                        if user.settings.isAiMode then
                                            feedRepository.markFeedAsRead(feeds.map(_.link), user)
                                        else IO.unit
                                    )
                                    .drain ++ summarizeStream(
                                    strippedText,
                                    validatedLanguage.displayName,
                                    selectedModel.modelId
                                )
                        )
                }
            }
            .handleErrorWith { error =>
                Stream.eval(logger.error(error)("Error in streamSummary")).drain ++
                    Stream.emit(
                        SummaryEvent.Error("Error generating summary: " + error.getMessage)
                    ) ++ Stream.emit(SummaryEvent.Done)
            }

    private def generateFunFact(user: User, modelId: String): IO[String] =
        val validatedLanguage = user.settings.summaryLanguage
            .flatMap(SummaryLanguage.fromString)
            .getOrElse(SummaryLanguage.English)

        val prompt = s"""Generate ONE short, interesting and surprising fun fact about technology, science, history, or nature.
                        |Make it educational and fascinating - something that would make someone say "wow, I didn't know that!"
                        |Keep it to 1-2 sentences maximum.
                        |Respond in ${validatedLanguage.displayName}.
                        |Do not use markdown formatting.
                        |Do not add any introduction or preamble, just state the fact directly.""".stripMargin

        buildGeminiRequest(modelId, prompt, temperature = Some(1.5)).flatMap { request =>
            client
                .run(request)
                .use { response =>
                    if response.status.isSuccess then
                        response
                            .as[GeminiResponse]
                            .map { geminiResp =>
                                geminiResp.candidates.headOption
                                    .flatMap(_.content.parts.flatMap(_.headOption))
                                    .map(_.text.trim)
                                    .filter(_.nonEmpty)
                                    .getOrElse("")
                            }
                    else
                        response.bodyText.compile.string.flatMap { body =>
                            logger.error(
                                s"Gemini API error (fun fact): status=${response.status}, body=$body"
                            ) *>
                                IO.pure("")
                        }
                }
                .handleErrorWith { error =>
                    logger.error(error)(s"Error generating fun fact: ${error.getMessage}") *>
                        IO.pure("")
                }
        }

    private def summarizeStream(
        text: String,
        language: String,
        modelId: String
    ): Stream[IO, SummaryEvent] =
        val prompt = s"""You must follow these rules for your response:
                        |1. Provide only the raw text of the code.
                        |2. Do NOT use any markdown formatting.
                        |3. Do NOT wrap the code in backticks (```)
                        |4. Do NOT add any text, notes, or explanations.
                        |5. Respond with HTML. Topic headings should be wrapped in <h4> tags, and paragraphs should be wrapped in <p> tags.
                        |6. Use <ul> and <li> tags for lists.
                        |7. Use <strong> tags for important text.
                        |8. Use <em> tags for emphasized text.
                        |9. Never use <script> tags.
                        |10. Group the summary by topic/category (e.g., Technology, Politics, Science, Entertainment, etc.).
                        |11. IMPORTANT: Add a suitable emoji at the beginning of each topic heading (e.g., "💻 Technology", "🏛️ Politics", "🔬 Science", "🎬 Entertainment", "💰 Business", "⚽ Sports", "🌍 World News", etc.).
                        |12. IMPORTANT: Deduplicate similar stories - if multiple feeds cover the same news event, combine them into a single summary entry.
                        |13. For each topic, list the key stories with brief summaries.
                        |Now, following these rules exactly summarize the following text. Answer in $language: $text.""".stripMargin

        Stream
            .eval(buildGeminiRequest(modelId, prompt, stream = true))
            .flatMap { request =>
                client.stream(request).flatMap { response =>
                    if response.status.isSuccess then
                        response.body
                            .through(fs2.text.utf8.decode)
                            .through(io.circe.fs2.stringArrayParser)
                            .through(io.circe.fs2.decoder[IO, GeminiResponse])
                            .map { geminiResp =>
                                geminiResp.candidates.headOption
                                    .flatMap(_.content.parts.flatMap(_.headOption))
                                    .map(_.text)
                                    .getOrElse("")
                            }
                            .map { text =>
                                if (text.startsWith("```html")) {
                                    text.stripPrefix("```html").stripSuffix("```").trim
                                } else {
                                    text.trim
                                }
                            }
                            .filter(_.nonEmpty)
                            .map(SummaryEvent.Content(_))
                    else
                        Stream
                            .eval(response.bodyText.compile.string.flatMap { body =>
                                logger.error(
                                    s"Gemini API stream error: status=${response.status}, body=$body"
                                )
                            })
                            .drain ++ Stream.emit(
                            SummaryEvent.Error(s"API error: ${response.status.reason}")
                        )
                }
            }
            .handleErrorWith { error =>
                val errorMessage = error match
                    case _: TimeoutException =>
                        "Summary request timed out."
                    case _ =>
                        "Error communicating with the summary API."
                Stream
                    .eval(logger.error(error)(s"Error summarizing text: ${error.getMessage}"))
                    .drain ++
                    Stream.emit(SummaryEvent.Error(errorMessage))
            } ++ Stream.emit(SummaryEvent.Done)

