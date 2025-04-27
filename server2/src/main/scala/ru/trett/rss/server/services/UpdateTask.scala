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

    private def task: IO[Int] = {
        for {
            _ <- logger.info("Starting update task...")
            users <- userService.getUsers
            _ <- logger.info(s"Found ${users.size} users")
            _ <- users.traverse { user =>
                for {
                    channels <- channelService.updateFeeds(user)
                    _ <- logger.info(s"Updated ${channels.size} channels for user ${user.email}")
                } yield channels.size
            }
        } yield users.size
    }

    val backgroundTask: Stream[IO, Int] =
        Stream.eval(task) ++
            Stream.awakeEvery[IO](10.minutes).evalMap(_ => task)

object UpdateTask:
    def apply(channelService: ChannelService, userService: UserService)(using
        LoggerFactory[IO]
    ): IO[List[Int]] =
        new UpdateTask(channelService, userService).backgroundTask.compile.toList
