package ru.trett.rss.server.utils

import cats.effect.{IO, Resource}
import com.zaxxer.hikari.HikariConfig
import doobie.hikari.HikariTransactor
import org.flywaydb.core.Flyway
import org.testcontainers.containers.PostgreSQLContainer

/** Utility for creating test databases with Testcontainers PostgreSQL.
  */
object TestDatabase:

    private class PgContainer extends PostgreSQLContainer[PgContainer]("postgres:15-alpine")

    /** Creates a PostgreSQL container with Flyway migrations applied.
      *
      * @return
      *   Resource managing the database transactor and container lifecycle
      */
    def createTestTransactor(): Resource[IO, HikariTransactor[IO]] =
        for {
            container <- Resource.make(IO {
                val c = new PgContainer()
                c.start()
                c
            })(c => IO(c.stop()))
            _ <- Resource.eval(runMigrations(
                container.getJdbcUrl,
                container.getUsername,
                container.getPassword
            ))
            hikariConfig <- Resource.eval(IO {
                val config = new HikariConfig()
                config.setDriverClassName("org.postgresql.Driver")
                config.setJdbcUrl(container.getJdbcUrl)
                config.setUsername(container.getUsername)
                config.setPassword(container.getPassword)
                config.setMaximumPoolSize(10)
                config
            })
            xa <- HikariTransactor.fromHikariConfig[IO](hikariConfig)
        } yield xa

    private def runMigrations(jdbcUrl: String, username: String, password: String): IO[Unit] =
        IO {
            val flyway = Flyway
                .configure()
                .dataSource(jdbcUrl, username, password)
                .locations("classpath:db/migration")
                .load()
            flyway.migrate()
        }.void
