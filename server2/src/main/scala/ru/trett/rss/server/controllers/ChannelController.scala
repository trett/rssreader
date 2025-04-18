package ru.trett.rss.server.controllers

import cats.effect.IO
import io.circe.generic.auto.*
import org.http4s.AuthedRoutes
import org.http4s.circe.CirceEntityDecoder.circeEntityDecoder
import org.http4s.circe.CirceEntityEncoder.*
import org.http4s.dsl.io.*
import org.typelevel.log4cats.{LoggerFactory, SelfAwareStructuredLogger}
import ru.trett.rss.server.models.User
import ru.trett.rss.server.services.ChannelService

import scala.util.{Failure, Success, Try}

object ChannelController:

    def routes(channelService: ChannelService)(using LoggerFactory[IO]): AuthedRoutes[User, IO] =
        val logger: SelfAwareStructuredLogger[IO] = LoggerFactory[IO].getLogger
        AuthedRoutes.of {
            case GET -> Root / "api" / "channels" / "feeds" as user =>
                for {
                    _ <- logger.info("Fetching all channels for user: " + user.email)
                    channels <- channelService.getChannelsAndFeeds(user)
                    response <- Ok(channels)
                } yield response

            case GET -> Root / "api" / "channels" as user =>
                for {
                    _ <- logger.info("Fetching all channels for user: " + user.email)
                    channels <- channelService.getChannels(user)
                    response <- Ok(channels)
                } yield response

            case POST -> Root / "api" / "channels" / "refresh" as user =>
                for {
                    r <- channelService.updateFeeds(user)
                    response <- Ok(s"$r feeds updated")
                } yield response

            case req @ POST -> Root / "api" / "channels" as user =>
                for {
                    link <- req.req.as[String]
                    result <- channelService.createChannel(link, user)
                    response <- Ok(s"Channel created with result: $result")
                } yield response

            case DELETE -> Root / "api" / "channels" / LongVar(id) as user =>
                Try(channelService.removeChannel(id, user)) match {
                    case Failure(exception) =>
                        logger.error(exception)(
                            s"Error deleting channel: $id ${exception.getMessage}"
                        ) *> NotFound()
                    case Success(result) => Ok(result)
                }
        }
