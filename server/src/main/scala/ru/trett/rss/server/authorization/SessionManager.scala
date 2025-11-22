package ru.trett.rss.server.authorization

import cats.effect.*
import cats.effect.std.UUIDGen
import cats.syntax.all.*
import com.github.blemale.scaffeine.{Cache, Scaffeine}

import scala.concurrent.duration.DurationInt

case class SessionData(userEmail: String)

class SessionManager[F[_]: Sync] private (cache: Cache[String, SessionData]):

    def createSession(data: SessionData): F[String] =
        for {
            sessionId <- UUIDGen.randomString[F]
            _ <- Sync[F].delay(cache.put(sessionId, data))
        } yield sessionId

    def getSession(sessionId: String): F[Option[SessionData]] =
        Sync[F].delay(cache.getIfPresent(sessionId))

    def deleteSession(sessionId: String): F[Unit] =
        Sync[F].delay(cache.invalidate(sessionId))

object SessionManager:
    def apply[F[_]: Sync]: F[SessionManager[F]] =
        val cache: Cache[String, SessionData] = Scaffeine()
            .maximumSize(500)
            .expireAfterWrite(1.days)
            .build[String, SessionData]()
        new SessionManager(cache).pure[F]
