package ru.trett.rss.server.controllers

import cats.effect.IO
import org.http4s.AuthedRoutes
import org.http4s.circe.CirceEntityDecoder.*
import org.http4s.circe.CirceEntityEncoder.*
import org.http4s.dsl.io.*
import ru.trett.rss.server.models.User
import ru.trett.rss.server.services.FeedService

object FeedController:

    private object FilterQueryParamMatcher
        extends OptionalQueryParamDecoderMatcher[String]("filter")

    def routes(feedService: FeedService): AuthedRoutes[User, IO] =
        AuthedRoutes.of {
            case req @ POST -> Root / "api" / "feeds" / "read" as user =>
                for {
                    markRequest <- req.req.as[List[String]]
                    result <- feedService.markAsRead(markRequest, user)
                    response <- Ok(s"Feed marked as read: $result")
                } yield response

            case GET -> Root / "api" / "feeds" / "channel" / LongVar(
                    channelId
                ) / "unread" as user =>
                for {
                    count <- feedService.getUnreadCount(channelId, user.id)
                    response <- Ok(count)
                } yield response

            // Every channel's unread count in one call, so the sidebar doesn't fan out N requests.
            case GET -> Root / "api" / "feeds" / "unread" / "by-channel" :?
                FilterQueryParamMatcher(filter) as user =>
                for {
                    counts <- feedService.getUnreadCountByChannel(
                        user.id,
                        filter.contains("important")
                    )
                    response <- Ok(counts.map { case (id, count) => id.toString -> count })
                } yield response

            case GET -> Root / "api" / "feeds" / "unread" / "total" :? FilterQueryParamMatcher(
                    filter
                ) as user =>
                for {
                    count <- feedService.getTotalUnreadCount(user.id, filter.contains("important"))
                    response <- Ok(count)
                } yield response

            // Read and unread together — the count beside the sidebar's "All items".
            case GET -> Root / "api" / "feeds" / "total" :? FilterQueryParamMatcher(
                    filter
                ) as user =>
                for {
                    count <- feedService.getTotalCount(user.id, filter.contains("important"))
                    response <- Ok(count)
                } yield response
        }
