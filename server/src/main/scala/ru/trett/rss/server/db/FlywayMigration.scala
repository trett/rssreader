package ru.trett.rss.server.db

import cats.effect.*
import cats.implicits.*
import org.flywaydb.core.Flyway
import ru.trett.rss.server.config.DbConfig
import java.nio.file.{Files, Path}
import java.util.Comparator

object FlywayMigration:
    def migrate(config: DbConfig): IO[Unit] = {
        Resource
            .make(createTempDirWithMigrations())(deleteTempDir)
            .use { tempDir =>
                IO {
                    Flyway
                        .configure()
                        .dataSource(config.url, config.user, config.password)
                        .locations(s"filesystem:${tempDir.toAbsolutePath}")
                        .connectRetries(3)
                        .load()
                        .migrate()
                }
            }
            .void
    }

    private def createTempDirWithMigrations(): IO[Path] =
        for {
            tempDir <- IO(Files.createTempDirectory("migrations"))
            _ <- MigrationFiles.list.traverse { name =>
                val resourcePath = s"/db/migration/$name"
                IO(Option(getClass.getResourceAsStream(resourcePath))).flatMap {
                    case None =>
                        IO.raiseError(
                            new RuntimeException(s"Migration resource not found: $resourcePath")
                        )
                    case Some(is) =>
                        Resource.fromAutoCloseable(IO.pure(is)).use { stream =>
                            IO(Files.copy(stream, tempDir.resolve(name)))
                        }
                }
            }
        } yield tempDir

    private def deleteTempDir(path: Path): IO[Unit] = IO {
        if (Files.exists(path)) {
            val stream = Files.walk(path)
            try {
                stream
                    .sorted(Comparator.reverseOrder())
                    .forEach(Files.delete)
            } finally {
                stream.close()
            }
        }
    }
