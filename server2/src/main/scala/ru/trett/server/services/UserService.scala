package ru.trett.server.services

import cats.effect.IO
import ru.trett.reader.models.UserSettings
import ru.trett.server.models.User
import ru.trett.server.repositories.UserRepository

class UserService(userRepository: UserRepository):

    def createUser(id: String, name: String, email: String): IO[Int] =
        val user = User(id, name, email, User.Settings(7, false))
        userRepository.insertUser(user)

    def getUserById(id: String): IO[Option[User]] =
        userRepository.findUserById(id)

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
