package ru.trett.rss.server.authorization

import cats.data.*
import cats.effect.*
import cats.effect.std.MapRef
import cats.syntax.all.*
import org.http4s.*
import org.http4s.server.*
import org.typelevel.log4cats.LoggerFactory
import ru.trett.rss.server.models.User
import ru.trett.rss.server.services.UserService

import java.util.concurrent.ConcurrentHashMap

class AuthFilter[F[_]: Sync: LiftIO: LoggerFactory]:

    private val logger = LoggerFactory[F].getLogger

    private val cache: MapRef[F, String, Option[User]] =
        MapRef.fromConcurrentHashMap(new ConcurrentHashMap[String, User])

    def middleware(jwtManager: JwtManager, userService: UserService): AuthMiddleware[F, User] =
        AuthMiddleware(authUser(jwtManager, userService))

    def updateCache(user: User): F[Unit] = cache.updateKeyValueIfSet(user.email, _ => user)

    private def authUser(
        jwtManager: JwtManager,
        userService: UserService
    ): Kleisli[[A] =>> OptionT[F, A], Request[F], User] =
        Kleisli { req =>
            req.cookies.find(_.name == "sessionId") match {
                case Some(sessionId) =>
                    jwtManager.verifyToken(sessionId.content) match {
                        case Right(sessionData) =>
                            OptionT(getUser(sessionData.userEmail, userService))
                        case Left(e) =>
                            OptionT
                                .liftF(logger.error(e)("Failed to verify token"))
                                .flatMap(_ => OptionT.none[F, User])
                    }
                case None => OptionT.none[F, User]
            }
        }

    private def getUser(email: String, userService: UserService): F[Option[User]] =
        cache(email).get.flatMap {
            case Some(user) => user.some.pure[F]
            case None =>
                LiftIO[F]
                    .liftIO(userService.getUserByEmail(email))
                    .flatMap {
                        case Some(user) =>
                            cache(email).updateAndGet(_ => Some(user))
                        case None => none[User].pure[F]
                    }
        }

object AuthFilter:
    def apply[F[_]: Sync: LiftIO: LoggerFactory]: F[AuthFilter[F]] = new AuthFilter().pure[F]
