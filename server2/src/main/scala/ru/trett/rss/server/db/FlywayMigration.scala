package ru.trett.rss.server.db

import cats.effect.IO
import org.flywaydb.core.Flyway
import ru.trett.rss.server.config.DbConfig

object FlywayMigration:
    def migrate(config: DbConfig): IO[Unit] = {
        IO {
            Flyway
                .configure()
                .dataSource(config.url, config.user, config.password)
                .locations("db/migration")
                .load()
                .migrate()
        }.void
    }
