package ru.trett.rss.server.utils

import cats.effect.{IO, Resource}
import com.zaxxer.hikari.HikariConfig
import doobie.hikari.HikariTransactor
import org.testcontainers.containers.PostgreSQLContainer
import ru.trett.rss.server.db.FlywayMigration

/** Utility for creating test databases with Testcontainers PostgreSQL.
  */
object TestDatabase:

    private class PgContainer extends PostgreSQLContainer[PgContainer]("postgres:18-alpine")

    /** Creates a PostgreSQL container with Flyway migrations applied.
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
            _ <- Resource.eval(
                runMigrations(container.getJdbcUrl, container.getUsername, container.getPassword)
            )
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
        } yield xa

    private def runMigrations(jdbcUrl: String, username: String, password: String): IO[Unit] =
        FlywayMigration.migrate(
            ru.trett.rss.server.config
                .DbConfig("org.postgresql.Driver", jdbcUrl, username, password)
        )
