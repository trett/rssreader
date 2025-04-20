package ru.trett.rss.server.services

import cats.effect.IO
import org.typelevel.log4cats.LoggerFactory
import ru.trett.rss.models.UserSettings
import ru.trett.rss.server.models.User
import ru.trett.rss.server.repositories.UserRepository

import scala.util.{Failure, Success, Try}

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

    def removeUser(id: String): IO[Int] =
        userRepository.deleteUser(id)

    def getUserByEmail(email: String): IO[Option[User]] =
        Try(userRepository.findUserByEmail(email)) match {
            case Success(user) => user
            case Failure(_) =>
                LoggerFactory[IO].getLogger.error(s"User with email $email not found") *> IO.pure(
                    None
                )
        }

    def updateUserSettings(user: User, settings: UserSettings): IO[Int] =
        userRepository.updateUserSettings(
            user.copy(settings = User.Settings(settings.retentionDays, settings.hideRead))
        )
