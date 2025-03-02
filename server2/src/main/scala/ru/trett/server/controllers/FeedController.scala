package ru.trett.server.controllers

import cats.effect.IO
import org.http4s.AuthedRoutes
import org.http4s.dsl.io.*
import ru.trett.server.services.FeedService
import ru.trett.server.models.{User, Feed}
import io.circe.generic.auto._
import org.http4s.circe.CirceEntityEncoder._
import org.http4s.circe.CirceEntityDecoder._

object FeedController {

  case class CreateFeedRequest(
    channelId: Long,
    title: String,
    link: String,
    description: String
  )

  def routes(feedService: FeedService): AuthedRoutes[User, IO] =
    AuthedRoutes.of {
      // Get all feeds for a channel
      case GET -> Root / "api" / "feeds" / "channel" / LongVar(channelId) as _ =>
        for {
          feeds <- feedService.getFeedsByChannel(channelId)
          response <- Ok(feeds)
        } yield response

      // Get a specific feed by ID
      case GET -> Root / "api" / "feeds" / LongVar(id) as _ =>
        for {
          maybeFeed <- feedService.getFeedById(id)
          response <- maybeFeed match {
            case Some(feed) => Ok(feed)
            case None => NotFound(s"Feed with id $id not found")
          }
        } yield response

      // Create a new feed
      case req @ POST -> Root / "api" / "feeds" as _ =>
        for {
          createRequest <- req.req.as[CreateFeedRequest]
          result <- feedService.createFeed(
            createRequest.channelId,
            createRequest.title,
            createRequest.link,
            createRequest.description
          )
          response <- Created(s"Feed created with result: $result")
        } yield response

      // Mark a feed as read
      case POST -> Root / "api" / "feeds" / LongVar(id) / "read" as _ =>
        for {
          result <- feedService.markAsRead(id)
          response <- Ok(s"Feed marked as read: $result")
        } yield response

      // Get unread count for a channel
      case GET -> Root / "api" / "feeds" / "channel" / LongVar(channelId) / "unread" as _ =>
        for {
          count <- feedService.getUnreadCount(channelId)
          response <- Ok(count)
        } yield response

      // Delete a feed
      case DELETE -> Root / "api" / "feeds" / LongVar(id) as _ =>
        for {
          result <- feedService.removeFeed(id)
          response <- Ok(s"Feed deleted with result: $result")
        } yield response
    }
}