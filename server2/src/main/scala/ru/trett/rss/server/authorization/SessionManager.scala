package ru.trett.rss.server.authorization

import cats.effect.*
import cats.effect.std.{MapRef, UUIDGen}
import cats.syntax.all.*

import java.util.concurrent.ConcurrentHashMap

case class SessionData(userEmail: String)

class SessionManager[F[_]: Sync] private (sessions: MapRef[F, String, Option[SessionData]]):

    def createSession(data: SessionData): F[String] =
        for {
            sessionId <- UUIDGen.randomString[F]
            _ <- sessions(sessionId).update(_ => Some(data))
        } yield sessionId

    def getSession(sessionId: String): F[Option[SessionData]] =
        sessions(sessionId).get

    def deleteSession(sessionId: String): F[Unit] =
        sessions(sessionId).update(_ => None)

object SessionManager:
    def apply[F[_]: Sync]: F[SessionManager[F]] =
        new SessionManager(MapRef.fromConcurrentHashMap(new ConcurrentHashMap[String, SessionData]))
            .pure[F]
