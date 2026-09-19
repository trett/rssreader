package ru.trett.rss.server.controllers

import cats.effect.IO
import io.circe.Json
import io.circe.generic.auto.*
import io.circe.syntax.*
import org.http4s.Header
import org.http4s.HttpRoutes
import org.http4s.UrlForm
import org.http4s.circe.CirceEntityDecoder.*
import org.http4s.circe.CirceEntityEncoder.*
import org.http4s.dsl.io.*
import org.typelevel.ci.*
import org.typelevel.log4cats.{Logger, LoggerFactory}
import ru.trett.rss.server.authorization.{JwtManager, SessionData}
import ru.trett.rss.server.models.User
import ru.trett.rss.server.services.{FeedService, UserService}

import java.nio.charset.StandardCharsets
import java.time.{Duration, LocalDate, LocalTime, OffsetDateTime, ZoneOffset}
import java.util.Base64
import scala.concurrent.duration.*
import scala.util.Try

/** Minimal Model Context Protocol (JSON-RPC 2.0) endpoint supporting OAuth 2.0 / RFC 9728 discovery
  * so Gemini Desktop App, Claude, and other MCP clients can securely query news by date.
  */
class McpController(feedService: FeedService, userService: UserService, jwtManager: JwtManager)(
    using loggerFactory: LoggerFactory[IO]
):
    private val logger: Logger[IO] = loggerFactory.getLogger

    private val ServerName = "rssreader"
    private val ServerVersion = "1.0.0"
    private val DefaultProtocolVersion = "2025-06-18"
    private val NewsToolName = "get_news_by_date"
    private val CurrentTimeToolName = "get_current_time"
    private val DefaultLimit = 100
    private val MaxRange = Duration.ofHours(24)

    private val AccessTokenTtl: FiniteDuration = 30.days
    private val RefreshTokenTtl: FiniteDuration = 90.days
    private val AuthCodeTtl: FiniteDuration = 5.minutes
    private val corsHeader = Header.Raw(ci"Access-Control-Allow-Origin", "*")

    def routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
        case req @ GET -> Root / ".well-known" / "oauth-protected-resource" =>
            Ok(protectedResourceMetadata(getBaseUrl(req)), corsHeader)
        case req @ GET -> Root / ".well-known" / "oauth-protected-resource" / "mcp" =>
            Ok(protectedResourceMetadata(getBaseUrl(req)), corsHeader)
        case req @ GET -> Root / ".well-known" / "oauth-authorization-server" =>
            Ok(authorizationServerMetadata(getBaseUrl(req)), corsHeader)
        case req @ GET -> Root / ".well-known" / "openid-configuration" =>
            Ok(authorizationServerMetadata(getBaseUrl(req)), corsHeader)
        case req @ OPTIONS -> Root / ".well-known" / "oauth-protected-resource" =>
            corsPreflight
        case req @ OPTIONS -> Root / ".well-known" / "oauth-protected-resource" / "mcp" =>
            corsPreflight
        case req @ OPTIONS -> Root / ".well-known" / "oauth-authorization-server" =>
            corsPreflight
        case req @ OPTIONS -> Root / ".well-known" / "openid-configuration" =>
            corsPreflight
        case req @ POST -> Root / "oauth" / "token" =>
            handleTokenRequest(req)
        case req @ OPTIONS -> Root / "oauth" / "token" =>
            corsPreflight
        case req @ GET -> Root / "oauth" / "authorize" =>
            handleAuthorizeRequest(req)
        case req @ GET -> Root / "mcp" =>
            respondGet(req)
        case req @ POST -> Root / "mcp" =>
            respond(req)
        case req @ OPTIONS -> Root / "mcp" =>
            corsPreflight
    }

    private def corsPreflight: IO[org.http4s.Response[IO]] =
        IO.pure(
            org.http4s
                .Response[IO](org.http4s.Status.NoContent)
                .putHeaders(
                    corsHeader,
                    Header.Raw(ci"Access-Control-Allow-Methods", "GET, POST, OPTIONS"),
                    Header.Raw(
                        ci"Access-Control-Allow-Headers",
                        "Authorization, Content-Type, Accept"
                    )
                )
        )

    private def protectedResourceMetadata(baseUrl: String): Json =
        Json.obj(
            "resource" -> s"$baseUrl/mcp".asJson,
            "authorization_servers" -> Json.arr(baseUrl.asJson),
            "scopes_supported" -> Json.arr("read".asJson, "mcp".asJson),
            "bearer_methods_supported" -> Json.arr("header".asJson)
        )

    private def authorizationServerMetadata(baseUrl: String): Json =
        Json.obj(
            "issuer" -> baseUrl.asJson,
            "authorization_endpoint" -> s"$baseUrl/oauth/authorize".asJson,
            "token_endpoint" -> s"$baseUrl/oauth/token".asJson,
            "token_endpoint_auth_methods_supported" -> Json
                .arr("client_secret_basic".asJson, "client_secret_post".asJson),
            "grant_types_supported" -> Json.arr(
                "client_credentials".asJson,
                "authorization_code".asJson,
                "refresh_token".asJson
            ),
            "response_types_supported" -> Json.arr("code".asJson),
            "code_challenge_methods_supported" -> Json.arr("S256".asJson),
            "scopes_supported" -> Json.arr("read".asJson, "mcp".asJson)
        )

    private def getBaseUrl(req: org.http4s.Request[IO]): String =
        req.uri.scheme
            .map(_.value)
            .flatMap { scheme =>
                req.uri.authority.map(auth => s"$scheme://$auth")
            }
            .orElse {
                val scheme = req.headers
                    .get(ci"X-Forwarded-Proto")
                    .map(_.head.value)
                    .orElse(if req.isSecure.getOrElse(false) then Some("https") else None)
                    .getOrElse("http")
                val host = req.headers
                    .get(ci"X-Forwarded-Host")
                    .map(_.head.value)
                    .orElse(req.headers.get(ci"Host").map(_.head.value))
                host.map(h => s"$scheme://$h")
            }
            .getOrElse("http://localhost:8080")

    private def unauthorizedChallenge(
        req: org.http4s.Request[IO],
        invalidToken: Boolean
    ): IO[org.http4s.Response[IO]] =
        val baseUrl = getBaseUrl(req)
        val metadataUrl = s"$baseUrl/.well-known/oauth-protected-resource"
        val challenge =
            if invalidToken then
                s"""Bearer error="invalid_token", error_description="The access token is invalid or expired", resource_metadata="$metadataUrl""""
            else s"""Bearer resource_metadata="$metadataUrl""""
        logger.warn(s"Unauthorized MCP request") *>
            IO.pure(
                org.http4s
                    .Response[IO](org.http4s.Status.Unauthorized)
                    .putHeaders(Header.Raw(ci"WWW-Authenticate", challenge), corsHeader)
                    .withEntity("Unauthorized: OAuth Bearer token required")
            )

    private def respond(req: org.http4s.Request[IO]): IO[org.http4s.Response[IO]] =
        bearerToken(req) match
            case None => unauthorizedChallenge(req, invalidToken = false)
            case Some(t) =>
                resolveUser(t).flatMap {
                    case None       => unauthorizedChallenge(req, invalidToken = true)
                    case Some(user) => req.as[Json].flatMap(handle(user, _))
                }

    private def respondGet(req: org.http4s.Request[IO]): IO[org.http4s.Response[IO]] =
        bearerToken(req) match
            case None => unauthorizedChallenge(req, invalidToken = false)
            case Some(t) =>
                resolveUser(t).flatMap {
                    case None => unauthorizedChallenge(req, invalidToken = true)
                    case Some(user) =>
                        Ok(
                            Json.obj("status" -> "ok".asJson, "user" -> user.email.asJson),
                            corsHeader
                        )
                }

    private def bearerToken(req: org.http4s.Request[IO]): Option[String] =
        req.headers
            .get(ci"Authorization")
            .map(_.head.value)
            .collect {
                case header if header.startsWith("Bearer ") => header.stripPrefix("Bearer ")
            }

    private def resolveUser(token: String): IO[Option[User]] =
        jwtManager.verifyToken(token) match
            case Right(session)
                if !session.userEmail
                    .startsWith("code:") && !session.userEmail.startsWith("refresh:") =>
                userService.getUserByEmail(session.userEmail)
            case _ => IO.none

    private def oauthError(error: String, description: String): Json =
        Json.obj("error" -> error.asJson, "error_description" -> description.asJson)

    private def tokenSuccessResponse(user: User): IO[org.http4s.Response[IO]] =
        val accessToken = jwtManager.createToken(SessionData(user.email), AccessTokenTtl)
        val refreshToken =
            jwtManager.createToken(SessionData(s"refresh:${user.email}"), RefreshTokenTtl)
        val body = Json.obj(
            "access_token" -> accessToken.asJson,
            "token_type" -> "Bearer".asJson,
            "expires_in" -> AccessTokenTtl.toSeconds.asJson,
            "refresh_token" -> refreshToken.asJson,
            "scope" -> "read mcp".asJson
        )
        Ok(
            body,
            corsHeader,
            Header.Raw(ci"Cache-Control", "no-store"),
            Header.Raw(ci"Pragma", "no-cache")
        )

    private def extractBasicCredentials(req: org.http4s.Request[IO]): Option[(String, String)] =
        req.headers
            .get(ci"Authorization")
            .map(_.head.value)
            .collect {
                case header if header.startsWith("Basic ") =>
                    val encoded = header.stripPrefix("Basic ").trim
                    Try {
                        val decoded =
                            new String(Base64.getDecoder.decode(encoded), StandardCharsets.UTF_8)
                        val parts = decoded.split(":", 2)
                        if parts.length == 2 then Some((parts(0), parts(1))) else None
                    }.toOption.flatten
            }
            .flatten

    private def extractParams(req: org.http4s.Request[IO]): IO[Map[String, String]] =
        req.as[UrlForm]
            .map { form =>
                form.values.view.mapValues(_.headOption.getOrElse("")).toMap
            }
            .handleErrorWith { _ =>
                req.as[Json]
                    .map { json =>
                        json.asObject
                            .map(_.toMap.collect {
                                case (k, v) if v.isString => k -> v.asString.get
                                case (k, v) if v.isNumber => k -> v.toString
                            })
                            .getOrElse(Map.empty)
                    }
                    .handleError(_ => req.uri.query.params)
            }

    private def unauthorizedClient(description: String): IO[org.http4s.Response[IO]] =
        IO.pure(
            org.http4s
                .Response[IO](org.http4s.Status.Unauthorized)
                .putHeaders(Header.Raw(ci"WWW-Authenticate", """Basic realm="mcp""""), corsHeader)
                .withEntity(oauthError("invalid_client", description))
        )

    private def handleTokenRequest(req: org.http4s.Request[IO]): IO[org.http4s.Response[IO]] =
        extractParams(req).flatMap { params =>
            val basicCreds = extractBasicCredentials(req)
            val clientId = basicCreds.map(_._1).orElse(params.get("client_id")).getOrElse("")
            val clientSecret =
                basicCreds.map(_._2).orElse(params.get("client_secret")).getOrElse("")
            val grantType = params.getOrElse("grant_type", "")

            grantType match
                case "client_credentials" =>
                    if clientId.isEmpty || clientSecret.isEmpty then
                        unauthorizedClient("Missing client credentials")
                    else
                        userService.getUserByMcpClientId(clientId).flatMap {
                            case Some(user)
                                if user.settings.mcpClientSecret.contains(clientSecret) =>
                                logger.info(
                                    s"Issued MCP OAuth token via client_credentials for ${user.email}"
                                ) *>
                                    tokenSuccessResponse(user)
                            case _ =>
                                unauthorizedClient("Invalid client credentials")
                        }

                case "authorization_code" =>
                    params.get("code") match
                        case None =>
                            BadRequest(
                                oauthError("invalid_request", "Missing code parameter"),
                                corsHeader
                            )
                        case Some(code) =>
                            jwtManager.verifyToken(code) match
                                case Right(session) if session.userEmail.startsWith("code:") =>
                                    val email = session.userEmail.stripPrefix("code:")
                                    userService.getUserByEmail(email).flatMap {
                                        case Some(user)
                                            if clientId.nonEmpty && user.settings.mcpClientId
                                                .contains(clientId) &&
                                                clientSecret.nonEmpty && user.settings.mcpClientSecret
                                                    .contains(clientSecret) =>
                                            tokenSuccessResponse(user)
                                        case _ =>
                                            unauthorizedClient("Invalid client credentials or code")
                                    }
                                case _ =>
                                    BadRequest(
                                        oauthError("invalid_grant", "Invalid authorization code"),
                                        corsHeader
                                    )

                case "refresh_token" =>
                    params.get("refresh_token") match
                        case None =>
                            BadRequest(
                                oauthError("invalid_request", "Missing refresh_token parameter"),
                                corsHeader
                            )
                        case Some(rt) =>
                            jwtManager.verifyToken(rt) match
                                case Right(session) if session.userEmail.startsWith("refresh:") =>
                                    val email = session.userEmail.stripPrefix("refresh:")
                                    userService.getUserByEmail(email).flatMap {
                                        case Some(user) =>
                                            tokenSuccessResponse(user)
                                        case None =>
                                            BadRequest(
                                                oauthError("invalid_grant", "User not found"),
                                                corsHeader
                                            )
                                    }
                                case _ =>
                                    BadRequest(
                                        oauthError("invalid_grant", "Invalid refresh token"),
                                        corsHeader
                                    )

                case other =>
                    BadRequest(
                        oauthError(
                            "unsupported_grant_type",
                            s"Grant type '$other' is not supported"
                        ),
                        corsHeader
                    )
        }

    private def handleAuthorizeRequest(req: org.http4s.Request[IO]): IO[org.http4s.Response[IO]] =
        val params = req.uri.query.params
        val clientId = params.getOrElse("client_id", "")
        val redirectUri = params.get("redirect_uri")
        val state = params.get("state")
        if clientId.isEmpty then BadRequest("Missing client_id", corsHeader)
        else
            redirectUri match
                case None => BadRequest("Missing redirect_uri", corsHeader)
                case Some(redirect) =>
                    userService.getUserByMcpClientId(clientId).flatMap {
                        case None => BadRequest(s"Invalid client_id: $clientId", corsHeader)
                        case Some(user) =>
                            val authCode = jwtManager.createToken(
                                SessionData(s"code:${user.email}"),
                                AuthCodeTtl
                            )
                            val targetUri = org.http4s.Uri
                                .unsafeFromString(redirect)
                                .withQueryParam("code", authCode)
                            val finalUri =
                                state.fold(targetUri)(s => targetUri.withQueryParam("state", s))
                            SeeOther(org.http4s.headers.Location(finalUri))
                    }

    private def handle(user: User, request: Json): IO[org.http4s.Response[IO]] =
        val cursor = request.hcursor
        val method = cursor.get[String]("method").getOrElse("")
        logger.info(s"MCP request: method=$method, user=${user.email}") *> {
            // A JSON-RPC notification has no `id` and expects no response body.
            if !cursor.downField("id").succeeded then Accepted()
            else
                val id = cursor.get[Json]("id").getOrElse(Json.Null)
                method match
                    case "initialize" => Ok(success(id, initializeResult(request)))
                    case "ping"       => Ok(success(id, Json.obj()))
                    case "tools/list" => Ok(success(id, toolsListResult))
                    case "tools/call" => toolsCall(user, request).flatMap(r => Ok(success(id, r)))
                    case other =>
                        logger.warn(s"MCP unknown method '$other' from ${user.email}") *>
                            Ok(error(id, -32601, s"Method not found: $other"))
        }

    private def initializeResult(request: Json): Json =
        val protocolVersion = request.hcursor
            .downField("params")
            .get[String]("protocolVersion")
            .getOrElse(DefaultProtocolVersion)
        Json.obj(
            "protocolVersion" -> protocolVersion.asJson,
            "capabilities" -> Json.obj("tools" -> Json.obj()),
            "serverInfo" -> Json.obj("name" -> ServerName.asJson, "version" -> ServerVersion.asJson)
        )

    private def prop(tpe: String, description: String): Json =
        Json.obj("type" -> tpe.asJson, "description" -> description.asJson)

    private def tool(name: String, description: String, properties: Json): Json =
        Json.obj(
            "name" -> name.asJson,
            "description" -> description.asJson,
            "inputSchema" -> Json.obj(
                "type" -> "object".asJson,
                "properties" -> properties,
                "required" -> Json.arr()
            )
        )

    private val toolsListResult: Json =
        Json.obj(
            "tools" -> Json.arr(
                tool(
                    CurrentTimeToolName,
                    "Get the server's current time as an ISO-8601 datetime in UTC. Use it to " +
                        "resolve relative dates like 'today' or 'last 24 hours' before calling " +
                        NewsToolName + ".",
                    Json.obj()
                ),
                tool(
                    NewsToolName,
                    "Fetch important news items (flagged important, or from a highlighted " +
                        "channel) within a date range, newest first. Returns items across ALL your " +
                        "channels in one call, grouped by channel — you never need to query " +
                        "channels individually. Each group is a single feed, so detect that feed's " +
                        "language and translate accordingly. For the latest news, call with NO " +
                        "arguments: it returns the last 24 hours. The range spans at most 24 hours " +
                        "and defaults to the last 24 hours.",
                    Json.obj(
                        "from" -> prop(
                            "string",
                            "Optional start of the range, inclusive. ISO-8601 date (2026-07-01) or " +
                                "datetime (2026-07-01T00:00:00Z). Defaults to 24 hours before `to`."
                        ),
                        "to" -> prop(
                            "string",
                            "Optional end of the range, inclusive. ISO-8601 date or datetime. " +
                                "Defaults to now (UTC). Must be within 24 hours of `from`."
                        ),
                        "limit" -> prop(
                            "integer",
                            s"Maximum items per channel (default $DefaultLimit)."
                        )
                    )
                )
            )
        )

    private def toolsCall(user: User, request: Json): IO[Json] =
        val params = request.hcursor.downField("params")
        val args = params.downField("arguments")
        params.get[String]("name").getOrElse("") match
            case CurrentTimeToolName => currentTime(user)
            case NewsToolName        => getNewsByDate(user, args)
            case other =>
                logger.warn(s"MCP unknown tool '$other' from ${user.email}") *>
                    IO.pure(toolError(s"Unknown tool: $other"))

    private def currentTime(user: User): IO[Json] =
        IO.realTimeInstant.flatMap { instant =>
            val now = instant.atOffset(ZoneOffset.UTC)
            logger.info(s"MCP $CurrentTimeToolName: user=${user.email}, now=$now") *>
                IO.pure(toolText(now.toString))
        }

    private def getNewsByDate(user: User, args: io.circe.ACursor): IO[Json] =
        IO.realTimeInstant.flatMap { instant =>
            val now = instant.atOffset(ZoneOffset.UTC)
            def bound(field: String, default: OffsetDateTime, endOfDay: Boolean) =
                args
                    .get[String](field)
                    .toOption
                    .fold[Either[String, OffsetDateTime]](Right(default))(parseDate(_, endOfDay))
            val parsed = for
                to <- bound("to", now, endOfDay = true)
                from <- bound("from", to.minus(MaxRange), endOfDay = false)
                _ <- validateRange(from, to)
            yield (from, to)
            parsed match
                case Left(message) =>
                    logger.warn(
                        s"MCP $NewsToolName invalid arguments from ${user.email}: $message"
                    ) *> IO.pure(toolError(message))
                case Right((from, to)) =>
                    val limit =
                        args.get[Int]("limit").toOption.filter(_ > 0).getOrElse(DefaultLimit)
                    allChannels(user, from, to, limit)
        }

    private def allChannels(
        user: User,
        from: OffsetDateTime,
        to: OffsetDateTime,
        limit: Int
    ): IO[Json] =
        logger.info(
            s"MCP $NewsToolName: user=${user.email}, channelId=all, from=$from, to=$to, limit=$limit"
        ) *>
            feedService
                .getNewsByDateGrouped(user, from, to, limit)
                .flatTap(groups =>
                    logger.info(
                        s"MCP $NewsToolName returned ${groups.map(_.items.size).sum} items across " +
                            s"${groups.size} channels for ${user.email}"
                    )
                )
                .map(groups => toolText(groups.asJson.noSpaces))
                .handleErrorWith { e =>
                    logger.error(e)(s"MCP $NewsToolName failed for ${user.email}") *>
                        IO.pure(toolError(s"Failed to fetch news: ${e.getMessage}"))
                }

    /** The range must be ordered and span at most 24 hours. */
    private def validateRange(from: OffsetDateTime, to: OffsetDateTime): Either[String, Unit] =
        if to.isBefore(from) then Left("`to` must not be before `from`")
        else if Duration.between(from, to).compareTo(MaxRange) > 0 then
            Left("The date range must not exceed 24 hours")
        else Right(())

    /** Accepts a full ISO-8601 datetime, or a date-only value expanded to the start (or end) of day
      * in UTC so a date range is inclusive of the whole day.
      */
    private def parseDate(value: String, endOfDay: Boolean): Either[String, OffsetDateTime] =
        Try(OffsetDateTime.parse(value))
            .orElse {
                Try(LocalDate.parse(value)).map { date =>
                    val time = if endOfDay then LocalTime.MAX else LocalTime.MIN
                    date.atTime(time).atOffset(ZoneOffset.UTC)
                }
            }
            .toEither
            .left
            .map(_ => s"Invalid date '$value' (expected ISO-8601 date or datetime)")

    private def toolText(text: String): Json =
        Json.obj("content" -> Json.arr(textContent(text)))

    private def toolError(message: String): Json =
        Json.obj("content" -> Json.arr(textContent(message)), "isError" -> true.asJson)

    private def textContent(text: String): Json =
        Json.obj("type" -> "text".asJson, "text" -> text.asJson)

    private def success(id: Json, result: Json): Json =
        Json.obj("jsonrpc" -> "2.0".asJson, "id" -> id, "result" -> result)

    private def error(id: Json, code: Int, message: String): Json =
        Json.obj(
            "jsonrpc" -> "2.0".asJson,
            "id" -> id,
            "error" -> Json.obj("code" -> code.asJson, "message" -> message.asJson)
        )
