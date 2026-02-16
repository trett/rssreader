package ru.trett.rss.server.controllers

import cats.effect.IO
import cats.syntax.all.*
import org.http4s.HttpRoutes
import org.http4s.dsl.io.*
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.LoggerFactory
import ru.trett.rss.server.config.JobConfig
import ru.trett.rss.server.services.ChannelService
import ru.trett.rss.server.services.UserService
import org.typelevel.ci.*

class JobController(channelService: ChannelService, userService: UserService, config: JobConfig)(
    using loggerFactory: LoggerFactory[IO]
):
    private val logger: Logger[IO] = loggerFactory.getLogger

    def routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
        case req @ POST -> Root / "api" / "jobs" / "update" =>
            val token = req.headers.get(ci"Authorization").map(_.head.value)
            if (config.token.isEmpty || !token.contains(s"Bearer ${config.token}")) {
                logger.warn("Unauthorized job update attempt") *> Forbidden("Invalid token")
            } else {
                for {
                    _ <- logger.info("Starting scheduled feed update")
                    users <- userService.getUsers
                    _ <- logger.info(s"Found ${users.size} users")
                    channelCounts <- users.parTraverse { user =>
                        for {
                            channels <- channelService.updateFeeds(user)
                            _ <- logger.info(
                                s"Updated ${channels.size} channels for user ${user.email}"
                            )
                        } yield channels.size
                    }
                    total = channelCounts.sum
                    _ <- logger.info(
                        s"Finished scheduled update. Updated $total channels in total."
                    )
                    resp <- Ok(s"Updated $total channels")
                } yield resp
            }
    }
