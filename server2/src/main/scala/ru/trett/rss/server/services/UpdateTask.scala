package ru.trett.rss.server.services

import cats.effect.{Async, IO, Temporal}
import cats.syntax.all.*
import cats.syntax.apply.*
import cats.syntax.flatMap.*
import org.typelevel.log4cats.LoggerFactory

import scala.concurrent.duration.*

class UpdateTask[F[_]: Async: LoggerFactory](
    private val channelService: ChannelService,
    private val userService: UserService
):
    private def repeat: F[Unit] =
        schedule >> repeat

    private def schedule: F[Unit] =
        Temporal[F].sleep(10.second) *> {
            Async[F].delay(task())
        }

    private def task(): Unit = {
        val logger = LoggerFactory[F].getLogger
        println("Starting update task...")
        
//        for {
//            users <- userService.getUsers
//            _ <- users.traverse { user =>
//                for {
//                    channels <- channelService.updateFeeds(user)
//                } yield println(s"Updated ${channels.size} channels for user ${user.email}").pure[F]
//            }
//        } yield ()
        println("Update task completed.")
    }

object UpdateTask:
    def create[F[_]: Async: LoggerFactory](
        channelService: ChannelService,
        userService: UserService
    ): F[Unit] =
        new UpdateTask(channelService, userService).repeat
