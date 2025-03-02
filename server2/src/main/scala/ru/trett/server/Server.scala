package ru.trett.server

import cats.effect.*
import cats.implicits.*
import com.comcast.ip4s.*
import com.zaxxer.hikari.HikariConfig
import doobie.hikari.*
import org.http4s.AuthedRoutes
import org.http4s.HttpRoutes
import org.http4s.Method.*
import org.http4s.Uri
import org.http4s.ember.server.*
import org.http4s.implicits.*
import org.http4s.server.middleware.CORS
import org.typelevel.log4cats.*
import org.typelevel.log4cats.slf4j.*
import org.typelevel.log4cats.slf4j.Slf4jFactory
import ru.trett.server.authorization.AuthFilter
import ru.trett.server.authorization.SessionManager
import ru.trett.server.controllers.ChannelController
import ru.trett.server.controllers.LoginController
import ru.trett.server.models.User
import ru.trett.server.repositories.ChannelRepository
import ru.trett.server.repositories.FeedRepository
import ru.trett.server.repositories.UserRepository
import ru.trett.server.services.ChannelService
import ru.trett.server.services.FeedService
import ru.trett.server.services.UserService

import scala.concurrent.duration.*

object Server extends IOApp {

  given LoggerFactory[IO] = Slf4jFactory[IO]

  private val serverPort =
    Port.fromString(sys.env.getOrElse("SERVER_PORT", "8080"))

  private val corsPolicy = CORS.policy
    .withAllowOriginHost {
      _ == Uri.fromString(sys.env.getOrElse("CORS_URL", "https://localhost"))
    }
    .withAllowCredentials(false)
    .withAllowMethodsIn(Set(GET, POST, PUT, DELETE))
    .withMaxAge(1.day)

  private val logger: SelfAwareStructuredLogger[IO] =
    LoggerFactory[IO].getLogger

  private val transactor: Resource[IO, HikariTransactor[IO]] =
    for {
      hikariConfig <- Resource.pure {
        val config = new HikariConfig()
        config.setDriverClassName("org.postgresql.Driver")
        config.setJdbcUrl(
          sys.env.getOrElse(
            "DATASOURCE_URL",
            "jdbc:postgresql://postgresdb:5432/rss"
          )
        )
        config.setUsername(sys.env.getOrElse("DATASOURCE_USER", "rss_user"))
        config.setPassword(sys.env.getOrElse("DATASOURCE_PASS", "123456"))
        config
      }
      xa <- HikariTransactor.fromHikariConfig[IO](hikariConfig)
    } yield xa

  def authedRoutes(channelService: ChannelService): AuthedRoutes[User, IO] =
    ChannelController.routes(channelService)

  def routes(
      sessionManager: SessionManager[IO],
      channelService: ChannelService,
      userService: UserService
  ): HttpRoutes[IO] =
    LoginController.routes(sessionManager) <+> AuthFilter.middleware(
      sessionManager,
      userService
    )(
      authedRoutes(channelService)
    )

  override def run(args: List[String]): IO[ExitCode] =
    transactor.use { transactor =>
      for {
        sessionManager <- SessionManager.create[IO]
        channelRepository = ChannelRepository(transactor)
        channelService = ChannelService(channelRepository)
        feedRepository = FeedRepository(transactor)
        feedService = FeedService(feedRepository)
        userRepository = UserRepository(transactor)
        userService = UserService(userRepository)
        exitCode <- EmberServerBuilder
          .default[IO]
          .withHost(ipv4"0.0.0.0")
          .withPort(serverPort.get)
          .withHttpApp(
            corsPolicy(
              routes(sessionManager, channelService, userService)
            ).orNotFound
          )
          .build
          .use(_ => IO.never)
      } yield exitCode
    }
}
