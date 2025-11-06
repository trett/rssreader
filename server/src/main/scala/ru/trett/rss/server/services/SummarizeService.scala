package ru.trett.rss.server.services

import cats.effect.IO
import io.circe.Decoder
import io.circe.generic.auto.*
import io.circe.syntax.*
import org.http4s.syntax.all.uri
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
import ru.trett.rss.server.models.User
import ru.trett.rss.server.repositories.FeedRepository
import org.jsoup.Jsoup

case class Part(text: String)
case class Content(parts: List[Part])
case class Candidate(content: Content)
case class GeminiResponse(candidates: List[Candidate])

class SummarizeService(feedRepository: FeedRepository, client: Client[IO], apiKey: String)(using
    loggerFactory: LoggerFactory[IO]
):

    given Decoder[GeminiResponse] = Decoder.forProduct1("candidates")(GeminiResponse.apply)
    private val logger: Logger[IO] = LoggerFactory[IO].getLogger
    private val endpoint =
        uri"https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:generateContent"

    def getSummary(user: User): IO[String] = {
        for {
            feeds <- feedRepository.getUnreadFeeds(user)
            text = feeds.map(_.description).mkString("\n")
            strippedText = Jsoup.parse(text).text()
            summary <- summarize(strippedText, user.settings.summaryLanguage.getOrElse("English"))
        } yield summary
    }

    private def summarize(text: String, language: String): IO[String] = {
        val request = Request[IO](
            method = Method.POST,
            uri = endpoint,
            headers = Headers(
                Header.Raw(ci"X-goog-api-key", apiKey),
                Header.Raw(ci"Content-Type", "application/json")
            )
        )
            .withEntity(
                Map(
                    "contents" -> List(
                        Map(
                            "parts" -> List(
                                Map(
                                    "text" ->
                                        s"""
                                You must follow these rules for your response:
                                    1. Provide only the raw text of the code.
                                    2. Do NOT use any markdown formatting.
                                    3. Do NOT wrap the code in backticks (```)
                                    4. Do NOT add any text, notes, or explanations.
                                    5. Respond with HTML. Topic headings should be wrapped in <h4> tags, and paragraphs should be wrapped in <p> tags.
                                    6. Use <ul> and <li> tags for lists.
                                    7. Use <strong> tags for important text.
                                    8. Use <em> tags for emphasized text.
                                    9. Never use <script> tags.
                                    10. Group the summary by topic.
                                     Now, following these rules exactly summarize the following text. Answer in $language: $text."""
                                )
                            )
                        )
                    )
                ).asJson
            )

        client
            .expect[GeminiResponse](request)
            .map { response =>
                response.candidates.headOption
                    .flatMap(_.content.parts.headOption)
                    .map(_.text)
                    .map(text =>
                        text.startsWith("```html") match {
                            case true  => text.stripPrefix("```html").stripSuffix("```").trim
                            case false => text.trim
                        }
                    )
                    .getOrElse("Could not extract summary from response.")
            }
            .handleErrorWith { error =>
                logger.error(error)(s"Error summarizing text: $error") *> IO.pure(
                    "Error communicating with the summary API."
                )
            }
    }
