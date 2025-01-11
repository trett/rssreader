package ru.trett.server.authorization

import cats.*
import cats.data.*
import cats.effect.*
import cats.syntax.all.*
import org.http4s.*
import org.http4s.server.*

import java.util.UUID

case class User(id: UUID, email: String)

object AuthFilter {

  def authUser(
      sessionManager: SessionManager[IO]
  ): Kleisli[[A] =>> OptionT[IO, A], Request[IO], User] =
    Kleisli(req => {
      req.cookies.find(_.name == "sessionId") match {
        case Some(sessionId) =>
          OptionT
            .some(sessionId.content)
            .flatMapF(sessionManager.getSession(_))
            .map(sessionData => User(UUID.randomUUID(), sessionData.userEmail))
        case None => OptionT.none[IO, User]
      }
    })

  def middleware(sessionManager: SessionManager[IO]): AuthMiddleware[IO, User] =
    AuthMiddleware(authUser(sessionManager))
}
