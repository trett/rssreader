package ru.trett.server.controllers

import cats.effect.IO
import io.circe.generic.auto.*
import org.http4s.AuthedRoutes
import org.http4s.circe.CirceEntityDecoder.*
import org.http4s.circe.CirceEntityEncoder.*
import org.http4s.dsl.io.*
import ru.trett.server.models.User
import ru.trett.server.services.FeedService

object FeedController {

  case class MarkAsReadRequest(ids: List[Long])

  def routes(feedService: FeedService): AuthedRoutes[User, IO] =
    AuthedRoutes.of {
      // Get all feeds for a channel
      // case GET -> Root / "api" / "feeds" / "channel" / LongVar(channelId) as _ =>
      //   for {
      //     feeds <- feedService.getFeedsByChannel(channelId)
      //     response <- Ok(feeds)
      //   } yield response

      // Mark a feed as read
      case req @ POST -> Root / "api" / "feeds" / "read" as user =>
        for {
          markRequest <- req.req.as[MarkAsReadRequest]
          result <- feedService.markAsRead(markRequest.ids, user)
          response <- Ok(s"Feed marked as read: $result")
        } yield response

      // Get unread count for a channel
      case GET -> Root / "api" / "feeds" / "channel" / LongVar(channelId) / "unread" as _ =>
        for {
          count <- feedService.getUnreadCount(channelId)
          response <- Ok(count)
        } yield response
    }
}