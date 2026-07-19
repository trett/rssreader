package ru.trett.rss.server.controllers

import cats.effect.IO
import io.circe.Json
import io.circe.generic.auto.*
import io.circe.syntax.*
import org.http4s.HttpRoutes
import org.http4s.circe.CirceEntityDecoder.*
import org.http4s.circe.CirceEntityEncoder.*
import org.http4s.dsl.io.*
import org.typelevel.ci.*
import org.typelevel.log4cats.{Logger, LoggerFactory}
import ru.trett.rss.server.authorization.JwtManager
import ru.trett.rss.server.models.User
import ru.trett.rss.server.services.{ChannelService, FeedService, UserService}

import java.time.{Duration, LocalDate, LocalTime, OffsetDateTime, ZoneOffset}
import scala.util.Try

/** Minimal Model Context Protocol (JSON-RPC 2.0) endpoint so Claude can query news by date. Two
  * ways in, both carrying a per-user JWT: Claude Desktop reaches `/mcp` through the `mcp-remote`
  * bridge, which forwards an `Authorization: Bearer <jwt>` header; the claude.ai web custom
  * connector cannot set a header, so it hits `/mcp/<jwt>` with the token in the path. Lives in the
  * unprotected route group because it authenticates itself rather than via the session cookie.
  */
class McpController(
    feedService: FeedService,
    channelService: ChannelService,
    userService: UserService,
    jwtManager: JwtManager
)(using loggerFactory: LoggerFactory[IO]):
    private val logger: Logger[IO] = loggerFactory.getLogger

    private val ServerName = "rssreader"
    private val ServerVersion = "1.0.0"
    private val DefaultProtocolVersion = "2025-06-18"
    private val NewsToolName = "get_news_by_date"
    private val ChannelsToolName = "list_channels"
    private val DefaultLimit = 100
    private val MaxRange = Duration.ofHours(24)

    def routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
        // Desktop path: token is forwarded as `Authorization: Bearer <jwt>` by `mcp-remote`.
        case req @ POST -> Root / "mcp" =>
            respond(bearerToken(req), req)
        // Web path: the claude.ai custom connector cannot set a header, so the per-user token
        // is carried in the URL itself (e.g. `/mcp/<jwt>`).
        case req @ POST -> Root / "mcp" / token =>
            respond(Some(token), req)
    }

    private def respond(
        token: Option[String],
        req: org.http4s.Request[IO]
    ): IO[org.http4s.Response[IO]] =
        resolveUser(token).flatMap {
            case None =>
                logger.warn("Unauthorized MCP request") *> Forbidden("Invalid or missing token")
            case Some(user) =>
                req.as[Json].flatMap(handle(user, _))
        }

    private def bearerToken(req: org.http4s.Request[IO]): Option[String] =
        req.headers
            .get(ci"Authorization")
            .map(_.head.value)
            .collect {
                case header if header.startsWith("Bearer ") => header.stripPrefix("Bearer ")
            }

    private def resolveUser(token: Option[String]): IO[Option[User]] =
        token match
            case Some(t) =>
                jwtManager.verifyToken(t) match
                    case Right(session) => userService.getUserByEmail(session.userEmail)
                    case Left(_)        => IO.none
            case None => IO.none

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

    private val toolsListResult: Json =
        Json.obj(
            "tools" -> Json.arr(
                Json.obj(
                    "name" -> ChannelsToolName.asJson,
                    "description" -> ("List this user's subscribed channels (feeds) with their " +
                        "id and title. Call this first, then call " + NewsToolName + " once per " +
                        "channel using the returned id.").asJson,
                    "inputSchema" -> Json.obj("type" -> "object".asJson, "properties" -> Json.obj())
                ),
                Json.obj(
                    "name" -> NewsToolName.asJson,
                    "description" -> ("Fetch important news items for a single channel published " +
                        "within a date range (max 24 hours), newest first. Query one channel at a " +
                        "time: because every item in the response comes from the same feed, detect " +
                        "that feed's language and translate accordingly. Use " + ChannelsToolName +
                        " to discover channel ids.").asJson,
                    "inputSchema" -> Json.obj(
                        "type" -> "object".asJson,
                        "properties" -> Json.obj(
                            "channelId" -> Json.obj(
                                "type" -> "integer".asJson,
                                "description" -> ("Id of the channel to query, as returned by " +
                                    ChannelsToolName + ".").asJson
                            ),
                            "from" -> Json.obj(
                                "type" -> "string".asJson,
                                "description" -> ("Start of the range, inclusive. ISO-8601 date " +
                                    "(2026-07-01) or datetime (2026-07-01T00:00:00Z).").asJson
                            ),
                            "to" -> Json.obj(
                                "type" -> "string".asJson,
                                "description" -> ("End of the range, inclusive. ISO-8601 date " +
                                    "(2026-07-10) or datetime. A date-only value covers the whole " +
                                    "day. Must be within 24 hours of `from`.").asJson
                            ),
                            "limit" -> Json.obj(
                                "type" -> "integer".asJson,
                                "description" -> s"Maximum number of items (default $DefaultLimit).".asJson
                            )
                        ),
                        "required" -> Json.arr("channelId".asJson, "from".asJson, "to".asJson)
                    )
                )
            )
        )

    private def toolsCall(user: User, request: Json): IO[Json] =
        val params = request.hcursor.downField("params")
        val args = params.downField("arguments")
        params.get[String]("name").getOrElse("") match
            case ChannelsToolName => listChannels(user)
            case NewsToolName     => getNewsByDate(user, args)
            case other =>
                logger.warn(s"MCP unknown tool '$other' from ${user.email}") *>
                    IO.pure(toolError(s"Unknown tool: $other"))

    private def listChannels(user: User): IO[Json] =
        logger.info(s"MCP $ChannelsToolName: user=${user.email}") *>
            channelService
                .getChannels(user)
                .flatTap(channels =>
                    logger.info(
                        s"MCP $ChannelsToolName returned ${channels.size} channels for ${user.email}"
                    )
                )
                .map(channels => toolText(channels.asJson.spaces2))
                .handleErrorWith { e =>
                    logger.error(e)(s"MCP $ChannelsToolName failed for ${user.email}") *>
                        IO.pure(toolError(s"Failed to list channels: ${e.getMessage}"))
                }

    private def getNewsByDate(user: User, args: io.circe.ACursor): IO[Json] =
        val parsed = for
            channelId <- args
                .get[Long]("channelId")
                .left
                .map(_ => "Missing required argument: channelId")
            fromStr <- args.get[String]("from").left.map(_ => "Missing required argument: from")
            toStr <- args.get[String]("to").left.map(_ => "Missing required argument: to")
            from <- parseDate(fromStr, endOfDay = false)
            to <- parseDate(toStr, endOfDay = true)
            _ <- validateRange(from, to)
        yield (channelId, from, to)
        parsed match
            case Left(message) =>
                logger.warn(s"MCP $NewsToolName invalid arguments from ${user.email}: $message") *>
                    IO.pure(toolError(message))
            case Right((channelId, from, to)) =>
                val limit = args.get[Int]("limit").toOption.filter(_ > 0).getOrElse(DefaultLimit)
                logger.info(
                    s"MCP $NewsToolName: user=${user.email}, channelId=$channelId, from=$from, to=$to, limit=$limit"
                ) *>
                    feedService
                        .getFeedsByDateRange(user, channelId, from, to, limit)
                        .flatTap(items =>
                            logger.info(
                                s"MCP $NewsToolName returned ${items.size} items for ${user.email}"
                            )
                        )
                        .map(items => toolText(items.asJson.spaces2))
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
