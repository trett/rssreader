package ru.trett.rss.server.services

import cats.effect.IO
import cats.syntax.all.*
import fs2.Stream
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.SelfAwareStructuredLogger

import scala.concurrent.duration.*

class UpdateTask private (channelService: ChannelService, userService: UserService)(using
    LoggerFactory[IO]
):

    val logger: SelfAwareStructuredLogger[IO] = LoggerFactory[IO].getLogger

    private def updateChannels: IO[Int] = {
        for {
            users <- userService.getUsers
            _ <- logger.info(s"Found ${users.size} users")
            _ <- users.parTraverse { user =>
                for {
                    channels <- channelService.updateFeeds(user)
                    _ <- logger.info(s"Updated ${channels.size} channels for user ${user.email}")
                } yield channels.size
            }
        } yield users.size
    }

    val task: Stream[IO, Int] =
        Stream.eval(updateChannels) ++
            Stream.awakeEvery[IO](10.minutes).evalMap(_ => updateChannels)

    def job: Stream[IO, Int] =
        Stream.bracket(IO(logger.info("Starting background job")))(_ =>
            IO(logger.info("Stopping background job"))
        ) >> task

object UpdateTask:
    def apply(channelService: ChannelService, userService: UserService)(using
        LoggerFactory[IO]
    ): IO[List[Int]] =
        new UpdateTask(channelService, userService).job.compile.toList
