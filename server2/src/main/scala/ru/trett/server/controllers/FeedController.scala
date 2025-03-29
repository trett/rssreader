package ru.trett.server.controllers

import cats.effect.IO
import io.circe.generic.auto.*
import org.http4s.AuthedRoutes
import org.http4s.circe.CirceEntityDecoder.*
import org.http4s.circe.CirceEntityEncoder.*
import org.http4s.dsl.io.*
import ru.trett.server.models.User
import ru.trett.server.services.FeedService

object FeedController:

  case class MarkAsReadRequest(links: List[String])

  def routes(feedService: FeedService): AuthedRoutes[User, IO] =
    AuthedRoutes.of {
      case req @ POST -> Root / "api" / "feeds" / "read" as user =>
        for {
          markRequest <- req.req.as[MarkAsReadRequest]
          result <- feedService.markAsRead(markRequest.links, user)
          response <- Ok(s"Feed marked as read: $result")
        } yield response

      case GET -> Root / "api" / "feeds" / "channel" / LongVar(channelId) / "unread" as _ =>
        for {
          count <- feedService.getUnreadCount(channelId)
          response <- Ok(count)
        } yield response
    }