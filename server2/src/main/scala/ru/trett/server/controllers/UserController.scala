package ru.trett.server.controllers

import cats.effect.IO
import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import org.http4s.AuthedRoutes
import org.http4s.circe.CirceEntityDecoder.*
import org.http4s.circe.CirceEntityEncoder.*
import org.http4s.dsl.io.*
import ru.trett.server.models.User
import ru.trett.server.services.UserService
import ru.trett.reader.models.UserSettings

object UserController {

    given Decoder[UserSettings] = deriveDecoder[UserSettings]
    given Encoder[UserSettings] = deriveEncoder[UserSettings]

    def routes(userService: UserService): AuthedRoutes[User, IO] =
        AuthedRoutes.of {

            case req @ GET -> Root / "api" / "user" / "settings" as user =>
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
