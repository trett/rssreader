package ru.trett.rss.server.services

import cats.effect.IO
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.LoggerFactory
import ru.trett.rss.models.UserSettings
import ru.trett.rss.server.models.User
import ru.trett.rss.server.repositories.UserRepository

class UserService(userRepository: UserRepository)(using loggerFactory: LoggerFactory[IO]):

    private val logger: Logger[IO] = loggerFactory.getLogger

    def createUser(id: String, name: String, email: String): IO[Either[Throwable, Int]] =
        val user = User(id, name, email, User.Settings())
        userRepository
            .insertUser(user)
            .onError(err => logger.error(err)("Error occurred while inserting user"))

    def getUsers: IO[List[User]] =
        userRepository.findUsers()

    def getUserSettings(id: String): IO[Option[UserSettings]] =
        userRepository
            .findUserById(id)
            .map(
                _.map(user =>
                    UserSettings(
                        user.name,
                        user.settings.hideRead,
                        user.settings.summaryLanguage,
                        user.settings.aiMode
                    )
                )
            )

    // TODO
    def removeUser(id: String): IO[Int] =
        userRepository.deleteUser(id)

    def getUserByEmail(email: String): IO[Option[User]] =
        userRepository.findUserByEmail(email).flatMap {
            case Right(user) => IO.pure(user)
            case Left(err) =>
                logger.error(err)(s"User with email $email not found") *> IO.none
        }

    def updateUserSettings(user: User): IO[Int] =
        userRepository.updateUserSettings(user)
