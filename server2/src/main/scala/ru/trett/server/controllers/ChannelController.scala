package ru.trett.server.controllers

import cats.effect.IO
import org.http4s.AuthedRoutes
import org.http4s.dsl.io.*
import ru.trett.server.authorization.User
import ru.trett.server.services.ChannelService

object ChannelController {

  def routes(channelService: ChannelService): AuthedRoutes[User, IO] =
    AuthedRoutes.of {
      case GET -> Root / "api" / "channels" as user =>
        Ok("channels")
      case POST -> Root / "api" / "channels" / "refresh" as user =>
        Ok(s"channel: ")
      case POST -> Root / "api" / "channels" / url as user =>
        Ok(s"channel: ")
      case DELETE -> Root / "api" / "channels" / id as user =>
        Ok(s"channel: ")
    }

}
