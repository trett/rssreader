package ru.trett.server.controllers

import cats.effect.IO
import org.http4s.AuthedRoutes
import org.http4s.dsl.io.*
import ru.trett.server.services.ChannelService
import org.http4s.circe.CirceEntityEncoder._
import ru.trett.server.models.User
import ru.trett.server.models.Channel
import io.circe.generic.auto._
import org.http4s.circe.CirceEntityDecoder.circeEntityDecoder

object ChannelController {

  def routes(channelService: ChannelService): AuthedRoutes[User, IO] =
    AuthedRoutes.of {
      case GET -> Root / "api" / "channels" as user =>
        for {
          channels <- channelService.getAllChannels(user)
          response <- Ok(channels)
        } yield response

      case POST -> Root / "api" / "channels" / "refresh" as user =>
        Ok(s"channel: ")

      case req @ POST -> Root / "api" / "channels" as user =>
        for {
          channelReq <- req.req.as[Channel]
          result <- channelService.createChannel(
            channelReq.channelLink,
            channelReq.title,
            channelReq.link
          )
          response <- Ok(s"Channel created with result: $result")
        } yield response

      case DELETE -> Root / "api" / "channels" / LongVar(id) as user =>
        for {
          result <- channelService.removeChannel(id)
          response <- Ok(s"Channel deleted with result: $result")
        } yield response
    }
}
