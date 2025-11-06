package ru.trett.rss.server.controllers

import cats.effect.IO
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}
import org.http4s.AuthedRoutes
import org.http4s.circe.CirceEntityDecoder.*
import org.http4s.circe.CirceEntityEncoder.*
import org.http4s.dsl.io.*
import org.typelevel.log4cats.{LoggerFactory, SelfAwareStructuredLogger}
import ru.trett.rss.models.UserSettings
import ru.trett.rss.server.models.User
import ru.trett.rss.server.services.UserService

object UserController {

    given Decoder[UserSettings] = deriveDecoder[UserSettings]
    given Encoder[UserSettings] = deriveEncoder[UserSettings]

    def routes(userService: UserService, cacheUpdater: User => IO[Unit])(using
        LoggerFactory[IO]
    ): AuthedRoutes[User, IO] =
        val logger: SelfAwareStructuredLogger[IO] = LoggerFactory[IO].getLogger
        AuthedRoutes.of {
            case GET -> Root / "api" / "user" / "settings" as user =>
                for {
                    settings <- userService.getUserSettings(user.id)
                    response <- Ok(settings)
                } yield response

            case req @ POST -> Root / "api" / "user" / "settings" as user =>
                for {
                    settings <- req.req.as[UserSettings]
                    updatedUser = user.copy(settings =
                        User.Settings(
                            settings.retentionDays,
                            settings.hideRead,
                            settings.summaryLanguage
                        )
                    )
                    result <- userService.updateUserSettings(updatedUser)
                    _ <- logger.info(
                        s"User: ${user.email} was updated with settings: ${user.settings}"
                    )
                    _ <- cacheUpdater(updatedUser)
                    response <- Ok(s"User created with result: $result")
                } yield response
        }
}
