package ru.trett.rss.server.services

import cats.effect.IO
import org.typelevel.log4cats.LoggerFactory
import ru.trett.rss.models.UserSettings
import ru.trett.rss.server.models.User
import ru.trett.rss.server.repositories.UserRepository

class UserService(userRepository: UserRepository)(using LoggerFactory[IO]):

    def createUser(id: String, name: String, email: String): IO[Int] =
        val user = User(id, name, email, User.Settings())
        userRepository.insertUser(user)

    def getUsers: IO[List[User]] =
        userRepository.findUsers()

    def getUserSettings(id: String): IO[Option[UserSettings]] =
        userRepository.findUserById(id).map {
            case Some(user) =>
                Some(UserSettings(user.name, user.settings.retentionDays, user.settings.hideRead))
            case None => None
        }

    // TODO
    def removeUser(id: String): IO[Int] =
        userRepository.deleteUser(id)

    def getUserByEmail(email: String): IO[Option[User]] =
        userRepository.findUserByEmail(email).flatMap {
            case Right(user) => IO.pure(user)
            case Left(_) =>
                LoggerFactory[IO].getLogger.error(s"User with email $email not found") *> IO.none
        }

    def updateUserSettings(user: User, settings: UserSettings): IO[Int] =
        userRepository.updateUserSettings(
            user.copy(settings = User.Settings(settings.retentionDays, settings.hideRead))
        )
