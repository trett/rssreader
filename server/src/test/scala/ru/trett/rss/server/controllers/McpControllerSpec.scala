package ru.trett.rss.server.controllers

import cats.effect.*
import cats.effect.unsafe.implicits.global
import io.circe.Json
import io.circe.syntax.*
import org.http4s.*
import org.http4s.circe.CirceEntityDecoder.*
import org.http4s.circe.CirceEntityEncoder.*
import org.http4s.headers.Authorization
import org.http4s.implicits.*
import org.scalamock.scalatest.MockFactory
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.typelevel.ci.*
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

    private val testClientId = "mcp_test_client_id"
    private val testClientSecret = "mcp_test_client_secret"
    private val user = User(
        "user-id",
        "Test User",
        "test@example.com",
        User.Settings(mcpClientId = Some(testClientId), mcpClientSecret = Some(testClientSecret))
    )
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
            override def getUserByMcpClientId(clientId: String): IO[Option[User]] =
                IO.pure(if clientId == testClientId then Some(user) else None)
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

    test("rejects request without a token with 401 and WWW-Authenticate header") {
        val response = post(rpc("tools/list"), withToken = false)
        response.status shouldBe Status.Unauthorized
        val authHeader = response.headers.get(ci"WWW-Authenticate").map(_.head.value)
        authHeader shouldBe defined
        authHeader.get should include("Bearer resource_metadata=")
        authHeader.get should include(".well-known/oauth-protected-resource")
    }

    test("rejects request with an invalid token with 401 and error=invalid_token") {
        val request = Request[IO](Method.POST, uri"/mcp")
            .withEntity(rpc("tools/list"))
            .withHeaders(Authorization(Credentials.Token(AuthScheme.Bearer, "garbage")))
        val response = controller.routes.orNotFound.run(request).unsafeRunSync()
        response.status shouldBe Status.Unauthorized
        val authHeader = response.headers.get(ci"WWW-Authenticate").map(_.head.value)
        authHeader shouldBe defined
        authHeader.get should include("error=\"invalid_token\"")
    }

    test("rejects requests to URL path /mcp/:token with 404 Not Found") {
        val request = Request[IO](Method.POST, uri"/mcp" / token).withEntity(rpc("tools/list"))
        val response = controller.routes.orNotFound.run(request).unsafeRunSync()
        response.status shouldBe Status.NotFound
    }

    test("GET /.well-known/oauth-protected-resource returns RFC 9728 metadata") {
        val request =
            Request[IO](Method.GET, uri"http://localhost:8080/.well-known/oauth-protected-resource")
        val response = controller.routes.orNotFound.run(request).unsafeRunSync()
        response.status shouldBe Status.Ok
        val json = response.as[Json].unsafeRunSync()
        json.hcursor.get[String]("resource").toOption shouldBe Some("http://localhost:8080/mcp")
        val authServers = json.hcursor
            .downField("authorization_servers")
            .values
            .toList
            .flatten
            .flatMap(_.asString)
        authServers should contain("http://localhost:8080")
    }

    test("GET /.well-known/oauth-authorization-server returns RFC 8414 metadata") {
        val request = Request[IO](
            Method.GET,
            uri"http://localhost:8080/.well-known/oauth-authorization-server"
        )
        val response = controller.routes.orNotFound.run(request).unsafeRunSync()
        response.status shouldBe Status.Ok
        val json = response.as[Json].unsafeRunSync()
        json.hcursor.get[String]("token_endpoint").toOption shouldBe Some(
            "http://localhost:8080/oauth/token"
        )
        val grantTypes = json.hcursor
            .downField("grant_types_supported")
            .values
            .toList
            .flatten
            .flatMap(_.asString)
        grantTypes should contain("client_credentials")
        grantTypes should not contain "authorization_code"
    }

    test("POST /oauth/token with client_credentials issues access token and accesses /mcp") {
        val request = Request[IO](Method.POST, uri"/oauth/token")
            .withEntity(
                UrlForm(
                    "grant_type" -> "client_credentials",
                    "client_id" -> testClientId,
                    "client_secret" -> testClientSecret
                )
            )
        val response = controller.routes.orNotFound.run(request).unsafeRunSync()
        response.status shouldBe Status.Ok
        val json = response.as[Json].unsafeRunSync()
        json.hcursor.get[String]("token_type").toOption shouldBe Some("Bearer")
        val accessToken = json.hcursor.get[String]("access_token").toOption
        accessToken shouldBe defined

        // Use the issued access token to call /mcp
        val mcpRequest = Request[IO](Method.POST, uri"/mcp")
            .withEntity(rpc("tools/list"))
            .withHeaders(Authorization(Credentials.Token(AuthScheme.Bearer, accessToken.get)))
        val mcpResponse = controller.routes.orNotFound.run(mcpRequest).unsafeRunSync()
        mcpResponse.status shouldBe Status.Ok
        mcpResponse.headers.get(ci"Access-Control-Allow-Origin").map(_.head.value) shouldBe Some(
            "*"
        )
    }

    test("POST /oauth/token with JSON body issues access token") {
        val jsonBody = Json.obj(
            "grant_type" -> "client_credentials".asJson,
            "client_id" -> testClientId.asJson,
            "client_secret" -> testClientSecret.asJson
        )
        val request = Request[IO](Method.POST, uri"/oauth/token")
            .withEntity(jsonBody)
        val response = controller.routes.orNotFound.run(request).unsafeRunSync()
        response.status shouldBe Status.Ok
        val json = response.as[Json].unsafeRunSync()
        json.hcursor.get[String]("access_token").toOption shouldBe defined
    }

    test("POST /oauth/token with Basic auth issues access token") {
        val basicAuth = java.util.Base64.getEncoder.encodeToString(
            s"$testClientId:$testClientSecret".getBytes(java.nio.charset.StandardCharsets.UTF_8)
        )
        val request = Request[IO](Method.POST, uri"/oauth/token")
            .withEntity(UrlForm("grant_type" -> "client_credentials"))
            .withHeaders(Authorization(Credentials.Token(ci"Basic", basicAuth)))
        val response = controller.routes.orNotFound.run(request).unsafeRunSync()
        response.status shouldBe Status.Ok
        val json = response.as[Json].unsafeRunSync()
        json.hcursor.get[String]("access_token").toOption shouldBe defined
    }

    test("POST /oauth/token rejects invalid client credentials with Cache-Control no-store") {
        val request = Request[IO](Method.POST, uri"/oauth/token")
            .withEntity(
                UrlForm(
                    "grant_type" -> "client_credentials",
                    "client_id" -> testClientId,
                    "client_secret" -> "wrong_secret"
                )
            )
        val response = controller.routes.orNotFound.run(request).unsafeRunSync()
        response.status shouldBe Status.Unauthorized
        response.headers.get(ci"Cache-Control").map(_.head.value) shouldBe Some("no-store")
        response.headers.get(ci"Access-Control-Allow-Origin").map(_.head.value) shouldBe Some("*")
    }

    test("rejects /oauth/authorize with 404 Not Found") {
        val request = Request[IO](
            Method.GET,
            uri"/oauth/authorize?response_type=code&client_id=mcp_test_client_id&redirect_uri=http://localhost:3000/callback&state=xyz"
        )
        val response = controller.routes.orNotFound.run(request).unsafeRunSync()
        response.status shouldBe Status.NotFound
    }

    test("initialize returns server info and echoes the protocol version") {
        val response =
            post(rpc("initialize", Json.obj("protocolVersion" -> Json.fromString("2025-06-18"))))
        response.status shouldBe Status.Ok
        response.headers.get(ci"Access-Control-Allow-Origin").map(_.head.value) shouldBe Some("*")
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
