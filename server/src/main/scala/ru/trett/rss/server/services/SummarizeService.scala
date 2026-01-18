package ru.trett.rss.server.services

import cats.effect.IO
import io.circe.Decoder
import io.circe.generic.auto.*
import io.circe.syntax.*
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
import ru.trett.rss.models.{
    SummaryLanguage,
    SummaryModel,
    SummaryResponse,
    SummaryResult,
    SummarySuccess,
    SummaryError
}
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

    private def getEndpoint(modelId: String): Uri =
        Uri.unsafeFromString(
            s"https://generativelanguage.googleapis.com/v1beta/models/$modelId:generateContent"
        )

    def getSummary(user: User, offset: Int): IO[SummaryResponse] =
        val selectedModel = user.settings.summaryModel
            .flatMap(SummaryModel.fromString)
            .getOrElse(SummaryModel.default)

        for
            totalUnread <- feedRepository.getTotalUnreadCount(user.id)
            feeds <- feedRepository.getUnreadFeeds(user, batchSize, offset)
            response <-
                if feeds.isEmpty && offset == 0 then
                    // No feeds at all - generate fun fact
                    generateFunFact(user, selectedModel.modelId).map(funFact =>
                        SummaryResponse(
                            result = SummarySuccess(""),
                            hasMore = false,
                            feedsProcessed = 0,
                            totalRemaining = 0,
                            funFact = Some(funFact)
                        )
                    )
                else if feeds.isEmpty then
                    // No more feeds (reached end of pagination)
                    IO.pure(
                        SummaryResponse(
                            result = SummarySuccess(""),
                            hasMore = false,
                            feedsProcessed = 0,
                            totalRemaining = 0,
                            funFact = None
                        )
                    )
                else
                    for
                        text <- IO.pure(feeds.map(_.description).mkString("\n"))
                        strippedText <- IO.pure(Jsoup.parse(text).text())
                        validatedLanguage = user.settings.summaryLanguage
                            .flatMap(SummaryLanguage.fromString)
                            .getOrElse(SummaryLanguage.English)
                        summaryResult <- summarize(
                            strippedText,
                            validatedLanguage.displayName,
                            selectedModel.modelId
                        )
                        // Mark feeds as read after successful summarization (only in AI mode)
                        _ <-
                            if user.settings.isAiMode && summaryResult.isInstanceOf[SummarySuccess]
                            then feedRepository.markFeedAsRead(feeds.map(_.link), user)
                            else IO.unit
                        remainingAfterThis = totalUnread - offset - feeds.size
                    yield SummaryResponse(
                        result = summaryResult,
                        hasMore = remainingAfterThis > 0,
                        feedsProcessed = feeds.size,
                        totalRemaining = Math.max(0, remainingAfterThis),
                        funFact = None
                    )
        yield response

    private def generateFunFact(user: User, modelId: String): IO[String] =
        val validatedLanguage = user.settings.summaryLanguage
            .flatMap(SummaryLanguage.fromString)
            .getOrElse(SummaryLanguage.English)

        val request = Request[IO](
            method = Method.POST,
            uri = getEndpoint(modelId),
            headers = Headers(
                Header.Raw(ci"X-goog-api-key", apiKey),
                Header.Raw(ci"Content-Type", "application/json")
            )
        ).withEntity(Map("contents" -> List(Map("parts" -> List(Map("text" -> s"""
                                    Generate ONE short, interesting and surprising fun fact about technology, science, history, or nature.
                                    Make it educational and fascinating - something that would make someone say "wow, I didn't know that!"
                                    Keep it to 1-2 sentences maximum.
                                    Respond in ${validatedLanguage.displayName}.
                                    Do not use markdown formatting.
                                    Do not add any introduction or preamble, just state the fact directly.
                                """))))).asJson)

        client
            .expect[GeminiResponse](request)
            .map { response =>
                response.candidates.headOption
                    .flatMap(_.content.parts.flatMap(_.headOption))
                    .map(_.text.trim)
                    .filter(_.nonEmpty)
                    .getOrElse("")
            }
            .handleErrorWith { error =>
                logger.error(error)(s"Error generating fun fact: $error") *>
                    IO.pure("")
            }

    private def summarize(text: String, language: String, modelId: String): IO[SummaryResult] =
        val request = Request[IO](
            method = Method.POST,
            uri = getEndpoint(modelId),
            headers = Headers(
                Header.Raw(ci"X-goog-api-key", apiKey),
                Header.Raw(ci"Content-Type", "application/json")
            )
        ).withEntity(
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
                                    10. Group the summary by topic/category (e.g., Technology, Politics, Science, Entertainment, etc.).
                                    11. IMPORTANT: Add a suitable emoji at the beginning of each topic heading (e.g., "💻 Technology", "🏛️ Politics", "🔬 Science", "🎬 Entertainment", "💰 Business", "⚽ Sports", "🌍 World News", etc.).
                                    12. IMPORTANT: Deduplicate similar stories - if multiple feeds cover the same news event, combine them into a single summary entry.
                                    13. For each topic, list the key stories with brief summaries.
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
                    .flatMap(_.content.parts.flatMap(_.headOption))
                    .map(_.text)
                    .map { text =>
                        if text.startsWith("```html") then
                            text.stripPrefix("```html").stripSuffix("```").trim
                        else text.trim
                    } match
                    case Some(html) if html.nonEmpty => SummarySuccess(html)
                    case _ => SummaryError("Could not extract summary from response.")
            }
            .handleErrorWith { error =>
                val errorMessage = error match
                    case _: TimeoutException =>
                        "Summary request timed out. The AI service is taking too long to respond. Please try again with fewer feeds."
                    case _ =>
                        "Error communicating with the summary API."
                logger.error(error)(s"Error summarizing text: $error") *> IO.pure(
                    SummaryError(errorMessage)
                )
            }
