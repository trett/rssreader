package ru.trett.server.controllers

import cats.effect.ExitCode
import cats.effect.IO
import cats.effect.IOApp
import cats.syntax.all.*
import com.comcast.ip4s.*
import org.http4s.AuthedRoutes
import org.http4s.HttpRoutes
import org.http4s.ember.server.*
import org.http4s.implicits.*
import org.typelevel.log4cats.*
import org.typelevel.log4cats.slf4j.*
import org.typelevel.log4cats.slf4j.Slf4jFactory
import ru.trett.server.authorization.AuthFilter
import ru.trett.server.authorization.SessionManager
import ru.trett.server.authorization.User
import ru.trett.server.repositories.ChannelRepository
import ru.trett.server.services.ChannelService

object Server extends IOApp {

  given LoggerFactory[IO] = Slf4jFactory[IO]
  val logger: SelfAwareStructuredLogger[IO] = LoggerFactory[IO].getLogger

  def authedRoutes(channelService: ChannelService): AuthedRoutes[User, IO] =
    ChannelController.routes(channelService)

  def routes(
      sessionManager: SessionManager[IO],
      channelService: ChannelService
  ): HttpRoutes[IO] =
    LoginController.routes(sessionManager) <+> AuthFilter.middleware(
      sessionManager
    )(
      authedRoutes(channelService)
    )

  override def run(args: List[String]): IO[ExitCode] =
    for {
      sessionManager <- SessionManager.create[IO]
      channelRepository = ChannelRepository()
      channelService = ChannelService(channelRepository, sessionManager)
      exitCode <- EmberServerBuilder
        .default[IO]
        .withHost(ipv4"0.0.0.0")
        .withPort(port"8080")
        .withHttpApp(routes(sessionManager, channelService).orNotFound)
        .build
        .use(_ => IO.never)
    } yield exitCode
}
