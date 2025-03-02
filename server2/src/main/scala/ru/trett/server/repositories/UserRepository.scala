package ru.trett.server.repositories

import cats.effect.IO
import doobie.hikari.HikariTransactor
import doobie.implicits.*
import doobie.postgres.implicits.*
import ru.trett.server.models.User

import java.util.UUID

class UserRepository(transactor: HikariTransactor[IO]) {

  def insertUser(user: User): IO[Int] = {
    sql"INSERT INTO users (id, name, email) VALUES (${user.id}, ${user.name}, ${user.email})".update.run
      .transact(transactor)
  }

  def findUserById(id: UUID): IO[Option[User]] = {
    sql"SELECT id, name, email FROM users WHERE id = $id"
      .query[User]
      .option
      .transact(transactor)
  }

  def deleteUser(id: UUID): IO[Int] = {
    sql"DELETE FROM users WHERE id = $id".update.run
      .transact(transactor)
  }

  def findUserByEmail(email: String): IO[Option[User]] = {
    sql"SELECT id, name, email FROM users WHERE email = $email"
      .query[User]
      .option
      .transact(transactor)
  }
}
