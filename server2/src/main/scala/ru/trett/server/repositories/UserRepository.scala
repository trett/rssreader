package ru.trett.server.repositories

import cats.effect.IO
import doobie.hikari.HikariTransactor
import doobie.implicits.*
import doobie.postgres.circe.jsonb.implicits.*
import doobie.util.Get
import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.syntax.*
import ru.trett.server.models.User

class UserRepository(xa: HikariTransactor[IO]):

    given Encoder[User] = deriveEncoder
    given Decoder[User] = deriveDecoder
    given Decoder[User.Settings] = deriveDecoder
    given Encoder[User.Settings] = deriveEncoder

    // given Read[Option[User]] = Read[(String, String, String, Json)].map {
    //   case (id, name, email, settings) =>
    //     decode[User.Settings](settings.noSpaces) match {
    //       case Right(decodedSettings) => Some(User(id, name, email, decodedSettings))
    //       case Left(value) => None
    //         // throw new RuntimeException(s"Failed to decode settings: $value")
    //     }
    // }

    def insertUser(user: User): IO[Int] =
        sql"""INSERT INTO users (id, name, email, settings) VALUES 
    (${user.id}, ${user.name}, ${user.email}, ${user.settings.asJson})""".update.run
            .transact(xa)

    def findUserById(id: String): IO[Option[User]] =
        sql"SELECT id, name, email, settings FROM users WHERE id = $id"
            .query[User]
            .option
            .transact(xa)

    def deleteUser(id: String): IO[Int] =
        sql"DELETE FROM users WHERE id = $id".update.run
            .transact(xa)

    def findUserByEmail(email: String): IO[Option[User]] =
        sql"SELECT id, name, email, settings::json FROM users WHERE email = $email"
            .query[User]
            .option
            .transact(xa)

    def updateUserSettings(user: User): IO[Int] =
        sql"UPDATE users SET settings = ${user.settings} WHERE id = ${user.id}".update.run
            .transact(xa)
