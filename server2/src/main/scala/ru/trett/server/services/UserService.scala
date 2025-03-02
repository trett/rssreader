package ru.trett.server.services

import cats.effect.IO
import ru.trett.server.models.User
import ru.trett.server.repositories.UserRepository
import java.util.UUID

class UserService(userRepository: UserRepository) {

  def createUser(name: String, email: String): IO[Int] = {
    val user = User(UUID.randomUUID(), name, email)
    userRepository.insertUser(user)
  }

  def getUserById(id: UUID): IO[Option[User]] = {
    userRepository.findUserById(id)
  }

  def removeUser(id: UUID): IO[Int] = {
    userRepository.deleteUser(id)
  }

  def getUserByEmail(email: String): IO[Option[User]] = {
    userRepository.findUserByEmail(email)
  }
}
