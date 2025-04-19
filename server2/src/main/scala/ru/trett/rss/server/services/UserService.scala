package ru.trett.rss.server.services

import cats.effect.IO
import ru.trett.rss.models.UserSettings
import ru.trett.rss.server.models.User
import ru.trett.rss.server.repositories.UserRepository

class UserService(userRepository: UserRepository):

    def createUser(id: String, name: String, email: String): IO[Int] =
        val user = User(id, name, email, User.Settings(7, false))
        userRepository.insertUser(user)

    def getUsers: IO[List[User]] =
        userRepository.findUsers()

    def getUserSettings(id: String): IO[Option[UserSettings]] =
        userRepository.findUserById(id).map {
            case Some(user) =>
                Some(UserSettings(user.name, user.settings.retentionDays, user.settings.read))
            case None => None
        }

    def removeUser(id: String): IO[Int] =
        userRepository.deleteUser(id)

    def getUserByEmail(email: String): IO[Option[User]] =
        userRepository.findUserByEmail(email)

    def updateUserSettings(user: User, settings: UserSettings): IO[Int] =
        userRepository.updateUserSettings(
            user.copy(settings = User.Settings(settings.retentionDays, settings.read))
        )
