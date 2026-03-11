package ru.trett.rss.server.utils

import cats.effect.{IO, Resource}
import cats.implicits.*
import com.zaxxer.hikari.HikariConfig
import doobie.hikari.HikariTransactor
import doobie.implicits.*
import org.testcontainers.containers.PostgreSQLContainer
import scala.io.Source

/** Utility for creating test databases with Testcontainers PostgreSQL.
  */
object TestDatabase:

    private class PgContainer extends PostgreSQLContainer[PgContainer]("postgres:18-alpine")

    /** Creates a PostgreSQL container with migrations applied.
      *
      * @return
      *   Resource managing the database transactor and container lifecycle
      */
    def createTestTransactor(): Resource[IO, HikariTransactor[IO]] =
        for {
            container <- Resource.make(IO.blocking {
                val c = new PgContainer()
                c.start()
                c
            })(c => IO.blocking(c.stop()))
            hikariConfig <- Resource.eval(IO.blocking {
                val config = new HikariConfig()
                config.setDriverClassName("org.postgresql.Driver")
                config.setJdbcUrl(container.getJdbcUrl)
                config.setUsername(container.getUsername)
                config.setPassword(container.getPassword)
                config.setMaximumPoolSize(10)
                config
            })
            xa <- HikariTransactor.fromHikariConfig[IO](hikariConfig)
            _ <- Resource.eval(runMigrations(xa))
        } yield xa

    private def runMigrations(xa: HikariTransactor[IO]): IO[Unit] =
        for {
            sql <- IO.blocking(Source.fromResource("db/init.sql").mkString)
            statements = sql
                .split(";")
                .map(_.trim)
                .filter(_.nonEmpty)
            _ <- statements.toList.traverse(s => doobie.Fragment.const(s).update.run.transact(xa))
        } yield ()
