package ru.trett.rss.server.controllers

import cats.effect.*
import cats.effect.unsafe.implicits.global
import io.circe.Json
import org.http4s.*
import org.http4s.circe.CirceEntityDecoder.*
import org.http4s.circe.CirceEntityEncoder.*
import org.http4s.headers.Authorization
import org.http4s.implicits.*
import org.scalamock.scalatest.MockFactory
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory
import ru.trett.rss.models.{ChannelNews, FeedItemData}
import ru.trett.rss.server.authorization.{JwtManager, SessionData}
import ru.trett.rss.server.models.User
import ru.trett.rss.server.repositories.{FeedRepository, UserRepository}
import ru.trett.rss.server.services.{FeedService, UserService}

import java.time.OffsetDateTime

class McpControllerSpec extends AnyFunSuite with Matchers with MockFactory {

    implicit val loggerFactory: LoggerFactory[IO] = Slf4jFactory.create[IO]

    private val user = User("user-id", "Test User", "test@example.com", User.Settings())
    private val jwtManager = new JwtManager("test-secret")
    private val token = jwtManager.createToken(SessionData(user.email))

    private val sampleItem = FeedItemData(
        link = "https://example.com/item1",
        channelTitle = "Channel",
        title = "Breaking news",
        description = "Something happened",
        pubDate = OffsetDateTime.parse("2026-07-05T10:00:00Z"),
        isRead = false
    )

    private val sampleGroup =
        ChannelNews(channelId = 12, channelTitle = "Channel", items = List(sampleItem))

    private def controller: McpController =
        val userService = new UserService(mock[UserRepository]) {
            override def getUserByEmail(email: String): IO[Option[User]] =
                IO.pure(if email == user.email then Some(user) else None)
        }
        val feedService = new FeedService(mock[FeedRepository]) {
            override def getNewsByDateGrouped(
                u: User,
                from: OffsetDateTime,
                to: OffsetDateTime,
                limitPerChannel: Int
            ): IO[List[ChannelNews]] = IO.pure(List(sampleGroup))
        }
        new McpController(feedService, userService, jwtManager)

    private def post(body: Json, withToken: Boolean = true): Response[IO] =
        val base = Request[IO](Method.POST, uri"/mcp").withEntity(body)
        val request =
            if withToken then
                base.withHeaders(Authorization(Credentials.Token(AuthScheme.Bearer, token)))
            else base
        controller.routes.orNotFound.run(request).unsafeRunSync()

    private def rpc(method: String, params: Json = Json.obj()): Json =
        Json.obj(
            "jsonrpc" -> Json.fromString("2.0"),
            "id" -> Json.fromInt(1),
            "method" -> Json.fromString(method),
            "params" -> params
        )

    test("rejects request without a token") {
        post(rpc("tools/list"), withToken = false).status shouldBe Status.Forbidden
    }

    test("rejects request with an invalid token") {
        val request = Request[IO](Method.POST, uri"/mcp")
            .withEntity(rpc("tools/list"))
            .withHeaders(Authorization(Credentials.Token(AuthScheme.Bearer, "garbage")))
        controller.routes.orNotFound.run(request).unsafeRunSync().status shouldBe Status.Forbidden
    }

    test("authenticates via a token in the URL path (web custom connector)") {
        val request = Request[IO](Method.POST, uri"/mcp" / token).withEntity(rpc("tools/list"))
        val response = controller.routes.orNotFound.run(request).unsafeRunSync()
        response.status shouldBe Status.Ok
        val names = response
            .as[Json]
            .unsafeRunSync()
            .hcursor
            .downField("result")
            .downField("tools")
            .values
            .toList
            .flatten
            .flatMap(_.hcursor.get[String]("name").toOption)
        names should contain("get_news_by_date")
    }

    test("rejects an invalid token in the URL path") {
        val request = Request[IO](Method.POST, uri"/mcp" / "garbage").withEntity(rpc("tools/list"))
        controller.routes.orNotFound.run(request).unsafeRunSync().status shouldBe Status.Forbidden
    }

    test("initialize returns server info and echoes the protocol version") {
        val response =
            post(rpc("initialize", Json.obj("protocolVersion" -> Json.fromString("2025-06-18"))))
        response.status shouldBe Status.Ok
        val body = response.as[Json].unsafeRunSync()
        val result = body.hcursor.downField("result")
        result.get[String]("protocolVersion").toOption shouldBe Some("2025-06-18")
        result.downField("serverInfo").get[String]("name").toOption shouldBe Some("rssreader")
    }

    test("tools/list advertises get_current_time and get_news_by_date only") {
        val body = post(rpc("tools/list")).as[Json].unsafeRunSync()
        val names = body.hcursor
            .downField("result")
            .downField("tools")
            .values
            .toList
            .flatten
            .flatMap(_.hcursor.get[String]("name").toOption)
        (names should contain).allOf("get_current_time", "get_news_by_date")
        names should not contain "list_channels"
    }

    test("tools/call get_current_time returns a UTC datetime as text content") {
        val params = Json.obj("name" -> Json.fromString("get_current_time"))
        val body = post(rpc("tools/call", params)).as[Json].unsafeRunSync()
        val content = body.hcursor.downField("result").downField("content").downArray
        content.get[String]("type").toOption shouldBe Some("text")
        val text = content.get[String]("text").toOption.get
        text should endWith("Z")
        noException should be thrownBy OffsetDateTime.parse(text)
    }

    test("tools/call get_news_by_date returns all channels grouped by channel") {
        val params = Json.obj("name" -> Json.fromString("get_news_by_date"))
        val body = post(rpc("tools/call", params)).as[Json].unsafeRunSync()
        val content = body.hcursor.downField("result").downField("content").downArray
        content.get[String]("type").toOption shouldBe Some("text")
        val text = content.get[String]("text").toOption.get
        text should include("\"channelId\":12")
        text should include("\"items\"")
        text should include("https://example.com/item1")
    }

    test("tools/call get_news_by_date with no arguments defaults to the last 24 hours") {
        val params = Json.obj("name" -> Json.fromString("get_news_by_date"))
        val body = post(rpc("tools/call", params)).as[Json].unsafeRunSync()
        body.hcursor.downField("result").get[Boolean]("isError").toOption shouldBe None
    }

    test("tools/call get_news_by_date rejects a range wider than 24 hours") {
        val params = Json.obj(
            "name" -> Json.fromString("get_news_by_date"),
            "arguments" -> Json.obj(
                "from" -> Json.fromString("2026-07-01"),
                "to" -> Json.fromString("2026-07-10")
            )
        )
        val body = post(rpc("tools/call", params)).as[Json].unsafeRunSync()
        val result = body.hcursor.downField("result")
        result.get[Boolean]("isError").toOption shouldBe Some(true)
        result.downField("content").downArray.get[String]("text").toOption.get should include(
            "24 hours"
        )
    }

    test("a notification (no id) is accepted with no body") {
        val notification = Json.obj(
            "jsonrpc" -> Json.fromString("2.0"),
            "method" -> Json.fromString("notifications/initialized")
        )
        post(notification).status shouldBe Status.Accepted
    }
}
