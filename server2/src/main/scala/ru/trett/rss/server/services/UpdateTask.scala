package ru.trett.rss.server.services

import cats.effect.{IO, Temporal}
import cats.syntax.all.*
import org.typelevel.log4cats.{LoggerFactory, SelfAwareStructuredLogger}

import scala.concurrent.duration.*

class UpdateTask private (channelService: ChannelService, userService: UserService)(
    using LoggerFactory[IO]
):

    val logger: SelfAwareStructuredLogger[IO] = LoggerFactory[IO].getLogger

    private def repeat: IO[Int] =
        schedule >> repeat

    private def schedule: IO[Int] =
        Temporal[IO].sleep(10.minutes) *> task

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

object UpdateTask:
    def apply(channelService: ChannelService, userService: UserService)(using
        LoggerFactory[IO]
    ): IO[Int] =
        new UpdateTask(channelService, userService).repeat
