package ru.trett.server.authorization

import cats.effect._
import cats.syntax.all._
import cats.effect.std.UUIDGen
import cats.effect.std.MapRef
import java.util.concurrent.ConcurrentHashMap

case class SessionData(userEmail: String, token: String)

class SessionManager[F[_]: Sync] private (
    sessions: MapRef[F, String, Option[SessionData]]
) {

  def createSession(data: SessionData): F[String] =
    for {
      sessionId <- UUIDGen.randomString
      _ <- sessions(sessionId).update(_ => Some(data))
    } yield sessionId

  def getSession(sessionId: String): F[Option[SessionData]] =
    sessions(sessionId).get

  def deleteSession(sessionId: String): F[Unit] =
    sessions(sessionId).update(_ => None)
}

object SessionManager {
  def create[F[_]: Sync]: F[SessionManager[F]] =
    new SessionManager(
      MapRef.fromConcurrentHashMap(new ConcurrentHashMap[String, SessionData])
    ).pure[F]
}
