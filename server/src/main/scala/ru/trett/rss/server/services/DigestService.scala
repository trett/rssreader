package ru.trett.rss.server.services

import cats.effect.IO
import cats.syntax.all.*
import org.http4s.client.Client
import org.typelevel.log4cats.LoggerFactory
import ru.trett.rss.server.config.DigestConfig
import java.time.{OffsetDateTime, ZoneOffset}

class DigestService(
    feedService: FeedService,
    userService: UserService,
    translator: TranslationService,
    mailer: DigestMailer
)(using loggerFactory: LoggerFactory[IO]):
    private val logger = loggerFactory.getLogger

    def send: IO[Unit] =
        for
            now <- IO.realTimeInstant
            to = OffsetDateTime.ofInstant(now, ZoneOffset.UTC)
            users <- userService.getUsers
            outcomes <- users.filter(_.settings.digestEmail.exists(_.nonEmpty)).traverse { user =>
                (for
                    channels <- feedService.getNewsByDateGrouped(
                        user,
                        to.minusDays(1),
                        to,
                        Int.MaxValue,
                        importantOnly = false
                    )
                    _ <-
                        if channels.isEmpty then IO.unit
                        else
                            for
                                key <- IO.fromOption(user.settings.geminiApiKey.filter(_.nonEmpty))(
                                    new IllegalStateException("Gemini API key is missing")
                                )
                                translated <- channels.traverse(translator.translate(_, key))
                                _ <- mailer.send(
                                    user.settings.digestEmail.get,
                                    s"Новости за сутки — ${to.toLocalDate}",
                                    DigestMailer.render(translated)
                                )
                            yield ()
                yield ()).attempt.flatTap {
                    case Left(_)  => logger.error(s"Daily digest failed for user ${user.id}")
                    case Right(_) => IO.unit
                }
            }
            _ <- IO.raiseWhen(outcomes.exists(_.isLeft))(
                new IllegalStateException("Some daily digests failed")
            )
        yield ()

object DigestService:
    def apply(
        feedService: FeedService,
        userService: UserService,
        client: Client[IO],
        config: DigestConfig
    )(using LoggerFactory[IO]): DigestService =
        new DigestService(
            feedService,
            userService,
            TranslationService(client),
            DigestMailer(config)
        )
