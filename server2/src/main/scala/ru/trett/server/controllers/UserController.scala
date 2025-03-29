package ru.trett.server.controllers

import cats.effect.IO
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import org.http4s.AuthedRoutes
import org.http4s.circe.CirceEntityDecoder.*
import org.http4s.dsl.io.*
import ru.trett.server.models.User
import ru.trett.server.services.UserService

object UserController {

  given Decoder[User.Settings] = deriveDecoder[User.Settings]

  def routes(userService: UserService): AuthedRoutes[User, IO] =
    AuthedRoutes.of {
      case req @ POST -> Root / "api" / "user" / "settings" as user =>
        for {
          settings <- req.req.as[User.Settings]
          result <- userService.updateUserSettings(user.id, settings)
          response <- Ok(s"User created with result: $result")
        } yield response
    }
}
