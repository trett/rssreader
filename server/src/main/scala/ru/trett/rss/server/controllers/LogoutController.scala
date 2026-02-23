package ru.trett.rss.server.controllers

import cats.effect.Async
import org.http4s.dsl.Http4sDsl
import cats.implicits.*
import org.http4s.AuthedRoutes
import org.typelevel.log4cats.LoggerFactory
import ru.trett.rss.server.models.User
import org.http4s.ResponseCookie

class LogoutController[F[_]: Async: LoggerFactory] extends Http4sDsl[F] {

    private val logger = LoggerFactory[F].getLogger

    val routes: AuthedRoutes[User, F] = AuthedRoutes.of {
        case authReq @ POST -> Root / "api" / "logout" as user =>
            for {
                _ <- logger.info(s"Logging out user: ${user.email}")
                res <- Ok().map(
                    _.addCookie(
                        ResponseCookie("sessionId", "", path = Some("/"), maxAge = Some(-1))
                    )
                )
            } yield res
    }
}
