package ru.trett.rss.server.controllers

import cats.effect.IO
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}
import org.http4s.AuthedRoutes
import org.http4s.circe.CirceEntityDecoder.*
import org.http4s.circe.CirceEntityEncoder.*
import org.http4s.dsl.io.*
import ru.trett.rss.models.UserSettings
import ru.trett.rss.server.models.User
import ru.trett.rss.server.services.UserService

object UserController {

    given Decoder[UserSettings] = deriveDecoder[UserSettings]
    given Encoder[UserSettings] = deriveEncoder[UserSettings]

    def routes(userService: UserService): AuthedRoutes[User, IO] =
        AuthedRoutes.of {
            case GET -> Root / "api" / "user" / "settings" as user =>
                for {
                    settings <- userService.getUserSettings(user.id)
                    response <- Ok(settings)
                } yield response

            case req @ POST -> Root / "api" / "user" / "settings" as user =>
                for {
                    settings <- req.req.as[UserSettings]
                    result <- userService.updateUserSettings(user, settings)
                    response <- Ok(s"User created with result: $result")
                } yield response
        }
}
