package ru.trett.server.services

import cats.effect.IO
import ru.trett.server.models.User
import ru.trett.server.repositories.UserRepository

class UserService(userRepository: UserRepository) {

  def createUser(id: String, name: String, email: String): IO[Int] = {
    val user = User(id, name, email)
    userRepository.insertUser(user)
  }

  def getUserById(id: String): IO[Option[User]] = {
    userRepository.findUserById(id)
  }

  def removeUser(id: String): IO[Int] = {
    userRepository.deleteUser(id)
  }

  def getUserByEmail(email: String): IO[Option[User]] = {
    userRepository.findUserByEmail(email)
  }
}
