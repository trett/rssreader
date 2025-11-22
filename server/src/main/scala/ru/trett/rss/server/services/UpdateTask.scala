package ru.trett.rss.server.services

import cats.effect.IO
import cats.syntax.all.*
import fs2.Stream
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.LoggerFactory

import scala.concurrent.duration.*

class UpdateTask private (channelService: ChannelService, userService: UserService)(using
    loggerFactory: LoggerFactory[IO]
):

    private val logger: Logger[IO] = loggerFactory.getLogger

    private def updateChannels(): IO[Int] = {
        for {
            users <- userService.getUsers
            _ <- logger.info(s"Found ${users.size} users")
            channelCounts <- users.parTraverse { user =>
                for {
                    channels <- channelService.updateFeeds(user)
                    _ <- logger.info(s"Updated ${channels.size} channels for user ${user.email}")
                } yield channels.size
            }
        } yield channelCounts.sum
    }

    private def taskStream: Stream[IO, Int] =
        Stream
            .awakeEvery[IO](10.minutes)
            .evalMap(_ => updateChannels())
            .handleErrorWith { error =>
                Stream.exec(logger.error(error)("Stream failed, restarting"))
            }

    private def job: Stream[IO, Unit] =
        Stream.bracket(logger.info("Starting background job"))(_ =>
            logger.info("Stopping background job")
        ) >> taskStream.drain

object UpdateTask:

    def apply(channelService: ChannelService, userService: UserService)(using
        loggerFactory: LoggerFactory[IO]
    ): IO[Unit] =
        new UpdateTask(channelService, userService).job.compile.drain
