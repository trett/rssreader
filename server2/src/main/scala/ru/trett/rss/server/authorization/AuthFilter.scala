package ru.trett.rss.server.authorization

import cats.*
import cats.data.*
import cats.effect.*
import cats.effect.std.MapRef
import cats.syntax.all.*
import org.http4s.*
import org.http4s.server.*
import ru.trett.rss.server.models.User
import ru.trett.rss.server.services.UserService

import java.util.concurrent.ConcurrentHashMap

class AuthFilter[F[_]: Sync: LiftIO]:

    private val cache: MapRef[F, String, Option[User]] =
        MapRef.fromConcurrentHashMap(new ConcurrentHashMap[String, User])

    def middleware(
        sessionManager: SessionManager[F],
        userService: UserService
    ): AuthMiddleware[F, User] =
        AuthMiddleware(authUser(sessionManager, userService))

    private def authUser(
        sessionManager: SessionManager[F],
        userService: UserService
    ): Kleisli[[A] =>> OptionT[F, A], Request[F], User] =
        Kleisli(req => {
            req.cookies.find(_.name == "sessionId") match {
                case Some(sessionId) =>
                    OptionT
                        .some(sessionId.content)
                        .flatMapF(sessionManager.getSession)
                        .flatMap(sessionData => {
                            val maybeUser = cache(sessionData.userEmail).get
                            val user = maybeUser.flatMap {
                                case Some(u) => Some(u).pure[F]
                                case None =>
                                    LiftIO[F]
                                        .liftIO(userService.getUserByEmail(sessionData.userEmail))
                                        .flatMap(u =>
                                            cache(sessionData.userEmail).updateAndGet(_ => u)
                                        )
                            }
                            OptionT(user)
                        })
                case None => OptionT.none[F, User]
            }
        })

object AuthFilter:
    def apply[F[_]: Sync: LiftIO]: F[AuthFilter[F]] = new AuthFilter().pure[F]
