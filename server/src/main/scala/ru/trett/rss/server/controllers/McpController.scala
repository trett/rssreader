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
import ru.trett.rss.server.services.{FeedService, UserService}

import java.time.{LocalDate, LocalTime, OffsetDateTime, ZoneOffset}
import scala.util.Try

/** Minimal Model Context Protocol (JSON-RPC 2.0) endpoint so Claude Desktop can query news by date.
  * Reached through the `mcp-remote` bridge, which forwards an `Authorization: Bearer <jwt>` header.
  * Lives in the unprotected route group because it authenticates itself (header-based) rather than
  * via the browser session cookie.
  */
class McpController(feedService: FeedService, userService: UserService, jwtManager: JwtManager)(
    using loggerFactory: LoggerFactory[IO]
):
    private val logger: Logger[IO] = loggerFactory.getLogger

    private val ServerName = "rssreader"
    private val ServerVersion = "1.0.0"
    private val DefaultProtocolVersion = "2025-06-18"
    private val ToolName = "get_news_by_date"
    private val DefaultLimit = 50

    def routes: HttpRoutes[IO] = HttpRoutes.of[IO] { case req @ POST -> Root / "mcp" =>
        authenticate(req).flatMap {
            case None =>
                logger.warn("Unauthorized MCP request") *> Forbidden("Invalid or missing token")
            case Some(user) =>
                req.as[Json].flatMap(handle(user, _))
        }
    }

    private def authenticate(req: org.http4s.Request[IO]): IO[Option[User]] =
        req.headers.get(ci"Authorization").map(_.head.value) match
            case Some(header) if header.startsWith("Bearer ") =>
                jwtManager.verifyToken(header.stripPrefix("Bearer ")) match
                    case Right(session) => userService.getUserByEmail(session.userEmail)
                    case Left(_)        => IO.none
            case _ => IO.none

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
                    "name" -> ToolName.asJson,
                    "description" -> ("Fetch this user's RSS news items published within a date " +
                        "range, newest first.").asJson,
                    "inputSchema" -> Json.obj(
                        "type" -> "object".asJson,
                        "properties" -> Json.obj(
                            "from" -> Json.obj(
                                "type" -> "string".asJson,
                                "description" -> ("Start of the range, inclusive. ISO-8601 date " +
                                    "(2026-07-01) or datetime (2026-07-01T00:00:00Z).").asJson
                            ),
                            "to" -> Json.obj(
                                "type" -> "string".asJson,
                                "description" -> ("End of the range, inclusive. ISO-8601 date " +
                                    "(2026-07-10) or datetime. A date-only value covers the whole " +
                                    "day.").asJson
                            ),
                            "limit" -> Json.obj(
                                "type" -> "integer".asJson,
                                "description" -> s"Maximum number of items (default $DefaultLimit).".asJson
                            ),
                            "importantOnly" -> Json.obj(
                                "type" -> "boolean".asJson,
                                "description" -> "Only return items flagged important (default false).".asJson
                            )
                        ),
                        "required" -> Json.arr("from".asJson, "to".asJson)
                    )
                )
            )
        )

    private def toolsCall(user: User, request: Json): IO[Json] =
        val params = request.hcursor.downField("params")
        val name = params.get[String]("name").getOrElse("")
        if name != ToolName then
            logger.warn(s"MCP unknown tool '$name' from ${user.email}") *>
                IO.pure(toolError(s"Unknown tool: $name"))
        else
            val args = params.downField("arguments")
            val parsed = for
                fromStr <- args.get[String]("from").left.map(_ => "Missing required argument: from")
                toStr <- args.get[String]("to").left.map(_ => "Missing required argument: to")
                from <- parseDate(fromStr, endOfDay = false)
                to <- parseDate(toStr, endOfDay = true)
            yield (from, to)
            parsed match
                case Left(message) =>
                    logger.warn(s"MCP $ToolName invalid arguments from ${user.email}: $message") *>
                        IO.pure(toolError(message))
                case Right((from, to)) =>
                    val limit =
                        args.get[Int]("limit").toOption.filter(_ > 0).getOrElse(DefaultLimit)
                    val importantOnly = args.get[Boolean]("importantOnly").getOrElse(false)
                    logger.info(
                        s"MCP $ToolName: user=${user.email}, from=$from, to=$to, limit=$limit, importantOnly=$importantOnly"
                    ) *>
                        feedService
                            .getFeedsByDateRange(user, from, to, limit, importantOnly)
                            .flatTap(items =>
                                logger.info(
                                    s"MCP $ToolName returned ${items.size} items for ${user.email}"
                                )
                            )
                            .map(items => toolText(items.asJson.spaces2))
                            .handleErrorWith { e =>
                                logger.error(e)(s"MCP $ToolName failed for ${user.email}") *>
                                    IO.pure(toolError(s"Failed to fetch news: ${e.getMessage}"))
                            }

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
