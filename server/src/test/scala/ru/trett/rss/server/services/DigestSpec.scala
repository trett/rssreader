package ru.trett.rss.server.services

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.circe.Json
import org.http4s.*
import org.http4s.client.Client
import org.http4s.circe.CirceEntityEncoder.*
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import ru.trett.rss.models.{ChannelNews, FeedItemData}
import java.time.OffsetDateTime

class DigestSpec extends AnyFunSuite with Matchers:
    private val item = FeedItemData(
        "https://example.com/news?a=1&b=2",
        "Channel",
        "Original",
        "Body",
        OffsetDateTime.now(),
        true
    )
    private val channel = ChannelNews(1, "Channel <script>", List(item))

    test(
        "renders channel groups and original links while escaping news and rejecting unsafe URLs"
    ) {
        val html = DigestMailer.render(
            List(
                channel.copy(items =
                    List(
                        item.copy(title = "<script>alert(1)</script>", description = "<img src=x>"),
                        item.copy(link = "javascript:alert(1)")
                    )
                )
            )
        )
        html should include("https://example.com/news?a=1&amp;b=2")
        html should include("&lt;script&gt;")
        (html should not).include("<script>")
        (html should not).include("javascript:")
        (html should not).include("<img")
    }

    private def translator(text: String, finish: String = "STOP") =
        val client = Client.fromHttpApp[IO](HttpApp[IO] { request =>
            request.headers.headers
                .find(_.name.toString.equalsIgnoreCase("x-goog-api-key"))
                .map(_.value) shouldBe Some("key")
            request.uri.query.renderString shouldBe ""
            IO.pure(
                Response[IO](Status.Ok).withEntity(
                    Json.obj(
                        "candidates" -> Json.arr(
                            Json.obj(
                                "finishReason" -> Json.fromString(finish),
                                "content" -> Json.obj(
                                    "parts" -> Json.arr(Json.obj("text" -> Json.fromString(text)))
                                )
                            )
                        )
                    )
                )
            )
        })
        TranslationService(client)

    test("translation preserves original link, read flag, and channel identity") {
        val translated = translator("""[{"title":"Новость","description":"Перевод"}]""")
            .translate(channel, "key")
            .unsafeRunSync()
        translated.items.head shouldBe item.copy(title = "Новость", description = "Перевод")
        translated.channelTitle shouldBe channel.channelTitle
    }

    test("rejects truncated, missing and malformed translations") {
        List(translator("[]"), translator("invalid"), translator("[]", "MAX_TOKENS")).foreach {
            service =>
                service.translate(channel, "key").attempt.unsafeRunSync().isLeft shouldBe true
        }
    }
