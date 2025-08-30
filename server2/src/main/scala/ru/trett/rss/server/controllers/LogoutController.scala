package ru.trett.rss.server.controllers

import cats.effect.Async
import org.http4s.dsl.Http4sDsl
import ru.trett.rss.server.authorization.SessionManager
import cats.implicits._
import org.http4s.AuthedRoutes
import org.typelevel.log4cats.LoggerFactory
import ru.trett.rss.server.models.User

class LogoutController[F[_]: Async: LoggerFactory](sessionManager: SessionManager[F]) extends Http4sDsl[F] {

  private val logger = LoggerFactory[F].getLogger

  val routes: AuthedRoutes[User, F] = AuthedRoutes.of { case authReq @ POST -> Root / "api" / "logout" as user =>
    authReq.req.cookies.find(_.name == "sessionId") match {
        case Some(cookie) =>
            for {
                _ <- logger.info(s"Deleting session for user: ${user.email}")
                _ <- sessionManager.deleteSession(cookie.content)
                res <- Ok()
            } yield res
        case None =>
            logger.warn(s"Logout request without session cookie for user: ${user.email}") >> Ok()
    }
  }

}
