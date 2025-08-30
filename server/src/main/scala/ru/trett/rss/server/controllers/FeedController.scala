package ru.trett.rss.server.controllers

import cats.effect.IO
import org.http4s.AuthedRoutes
import org.http4s.circe.CirceEntityDecoder.*
import org.http4s.circe.CirceEntityEncoder.*
import org.http4s.dsl.io.*
import ru.trett.rss.server.models.User
import ru.trett.rss.server.services.FeedService

object FeedController:

    def routes(feedService: FeedService): AuthedRoutes[User, IO] =
        AuthedRoutes.of {
            case req @ POST -> Root / "api" / "feeds" / "read" as user =>
                for {
                    markRequest <- req.req.as[List[String]]
                    result <- feedService.markAsRead(markRequest, user)
                    response <- Ok(s"Feed marked as read: $result")
                } yield response

            case GET -> Root / "api" / "feeds" / "channel" / LongVar(channelId) / "unread" as _ =>
                for {
                    count <- feedService.getUnreadCount(channelId)
                    response <- Ok(count)
                } yield response
        }
