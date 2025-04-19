package ru.trett.rss.server.repositories

import cats.effect.IO
import doobie.hikari.HikariTransactor
import doobie.implicits.*
import doobie.postgres.circe.jsonb.implicits.*
import doobie.util.Read
import io.circe.Json
import io.circe.parser.decode
import io.circe.syntax.*
import ru.trett.rss.server.models.User

class UserRepository(xa: HikariTransactor[IO]):

    given Read[Option[User]] = Read[(String, String, String, Json)].map {
        case (id, name, email, settings) =>
            decode[User.Settings](settings.noSpaces) match {
                case Right(decodedSettings) => Some(User(id, name, email, decodedSettings))
                case Left(_)                => None
            }
    }

    def insertUser(user: User): IO[Int] =
        sql"""INSERT INTO users (id, name, email, settings) VALUES
        (${user.id}, ${user.name}, ${user.email}, ${user.settings.asJson})""".update.run
            .transact(xa)

    def findUserById(id: String): IO[Option[User]] =
        sql"SELECT id, name, email, settings::json FROM users WHERE id = $id"
            .query[Option[User]]
            .unique
            .transact(xa)

    def findUsers(): IO[List[User]] =
        sql"SELECT id, name, email, settings::json FROM users"
            .query[Option[User]]
            .to[List]
            .transact(xa)
            .map(_.flatten)

    def deleteUser(id: String): IO[Int] =
        sql"DELETE FROM users WHERE id = $id".update.run
            .transact(xa)

    def findUserByEmail(email: String): IO[Option[User]] =
        sql"SELECT id, name, email, settings::json FROM users WHERE email = $email"
            .query[Option[User]]
            .unique
            .transact(xa)

    def updateUserSettings(user: User): IO[Int] =
        sql"UPDATE users SET settings = ${user.settings.asJson}::jsonb WHERE id = ${user.id}".update.run
            .transact(xa)
