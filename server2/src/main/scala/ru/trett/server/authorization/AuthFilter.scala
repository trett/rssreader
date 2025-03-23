package ru.trett.server.authorization

import cats.*
import cats.data.*
import cats.effect.*
import org.http4s.*
import org.http4s.server.*
import ru.trett.server.models.User
import ru.trett.server.services.UserService

object AuthFilter {

  def authUser(
      sessionManager: SessionManager[IO],
      userService: UserService
  ): Kleisli[[A] =>> OptionT[IO, A], Request[IO], User] =
    Kleisli(req => {
      
      req.cookies.find(_.name == "sessionId") match {
        case Some(sessionId) => {
          OptionT
            .some(sessionId.content)
            .flatMapF(sessionManager.getSession(_))
            .flatMapF(sessionData =>
              userService.getUserByEmail(sessionData.userEmail)
            )
        }
        case None => OptionT.none[IO, User]
      }
    })

  def middleware(
      sessionManager: SessionManager[IO],
      userService: UserService
  ): AuthMiddleware[IO, User] =
    AuthMiddleware(authUser(sessionManager, userService))
}
