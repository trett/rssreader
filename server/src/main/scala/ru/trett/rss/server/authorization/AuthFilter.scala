package ru.trett.rss.server.authorization

import cats.data.*
import cats.effect.*
import cats.syntax.all.*
import com.github.blemale.scaffeine.{Cache, Scaffeine}
import org.http4s.*
import org.http4s.server.*
import ru.trett.rss.server.models.User
import ru.trett.rss.server.services.UserService

import scala.concurrent.duration.*

class AuthFilter[F[_]: Sync: LiftIO] private (cache: Cache[String, User]):

    def middleware(
        sessionManager: SessionManager[F],
        userService: UserService
    ): AuthMiddleware[F, User] =
        AuthMiddleware(authUser(sessionManager, userService))

    def updateCache(user: User): F[Unit] = Sync[F].delay(cache.put(user.email, user))

    private def authUser(
        sessionManager: SessionManager[F],
        userService: UserService
    ): Kleisli[[A] =>> OptionT[F, A], Request[F], User] =
        Kleisli { req =>
            req.cookies.find(_.name == "sessionId") match {
                case Some(sessionId) =>
                    OptionT
                        .some(sessionId.content)
                        .flatMapF(sessionManager.getSession)
                        .flatMap(sessionData =>
                            OptionT(getUser(sessionData.userEmail, userService))
                        )
                case None => OptionT.none[F, User]
            }
        }

    private def getUser(email: String, userService: UserService): F[Option[User]] =
        Sync[F].delay(cache.getIfPresent(email)).flatMap {
            case Some(user) => user.some.pure[F]
            case None =>
                LiftIO[F]
                    .liftIO(userService.getUserByEmail(email))
                    .flatMap {
                        case Some(user) =>
                            Sync[F].delay(cache.put(email, user)).as(user.some)
                        case None => none[User].pure[F]
                    }
        }

object AuthFilter:
    def apply[F[_]: Sync: LiftIO]: F[AuthFilter[F]] =
        val cache: Cache[String, User] = Scaffeine()
            .maximumSize(100)
            .expireAfterWrite(1.hour)
            .build[String, User]()
        new AuthFilter(cache).pure[F]
