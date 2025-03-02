package ru.trett.server

import cats.effect.*
import cats.implicits.*
import com.comcast.ip4s.*
import com.zaxxer.hikari.HikariConfig
import doobie.hikari.*
import org.http4s.AuthedRoutes
import org.http4s.HttpRoutes
import org.http4s.Uri
import org.http4s.ember.server.*
import org.http4s.implicits.*
import org.http4s.server.middleware.CORS
import org.http4s.server.middleware.CORSPolicy
import org.typelevel.log4cats.*
import org.typelevel.log4cats.slf4j.*
import org.typelevel.log4cats.slf4j.Slf4jFactory
import pureconfig.ConfigSource
import ru.trett.server.authorization.AuthFilter
import ru.trett.server.authorization.SessionManager
import ru.trett.server.config.AppConfig
import ru.trett.server.config.CorsConfig
import ru.trett.server.config.DbConfig
import ru.trett.server.config.OAuthConfig
import ru.trett.server.controllers.ChannelController
import ru.trett.server.controllers.LoginController
import ru.trett.server.db.FlywayMigration
import ru.trett.server.models.User
import ru.trett.server.repositories.ChannelRepository
import ru.trett.server.repositories.FeedRepository
import ru.trett.server.repositories.UserRepository
import ru.trett.server.services.ChannelService
import ru.trett.server.services.FeedService
import ru.trett.server.services.UserService

object Server extends IOApp {

  given LoggerFactory[IO] = Slf4jFactory[IO]

  private val logger: SelfAwareStructuredLogger[IO] =
    LoggerFactory[IO].getLogger

  private def loadConfig: AppConfig =
    ConfigSource.default.load[AppConfig] match {
      case Right(config) => config
      case Left(error) =>
        throw new RuntimeException(s"Failed to load configuration: $error")
    }

  private def transactor(config: DbConfig): Resource[IO, HikariTransactor[IO]] =
    for {
      hikariConfig <- Resource.pure {
        val hikariConfig = new HikariConfig()
        hikariConfig.setDriverClassName(config.driver)
        hikariConfig.setJdbcUrl(config.url)
        hikariConfig.setUsername(config.user)
        hikariConfig.setPassword(config.password)
        hikariConfig
      }
      xa <- HikariTransactor.fromHikariConfig[IO](hikariConfig)
    } yield xa

  private def createCorsPolicy(config: CorsConfig): CORSPolicy =
    CORS.policy
      .withAllowOriginHost(_ == Uri.fromString(config.allowedOrigin))
      .withAllowCredentials(config.allowCredentials)
      .withMaxAge(config.maxAge)

  def authedRoutes(channelService: ChannelService): AuthedRoutes[User, IO] =
    ChannelController.routes(channelService)

  def routes(
      sessionManager: SessionManager[IO],
      channelService: ChannelService,
      userService: UserService,
      oauthConfig: OAuthConfig
  ): HttpRoutes[IO] =
    LoginController
      .routes(sessionManager, oauthConfig) <+> AuthFilter.middleware(
      sessionManager,
      userService
    )(
      authedRoutes(channelService)
    )

  override def run(args: List[String]): IO[ExitCode] =
    val appConfig = loadConfig
    transactor(appConfig.db).use { transactor =>
      for {
        _ <- FlywayMigration.migrate(appConfig.db)
        corsPolicy = createCorsPolicy(appConfig.cors)
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
          .withPort(Port.fromInt(appConfig.server.port).get)
          .withHttpApp(
            corsPolicy(
              routes(sessionManager, channelService, userService, appConfig.oauth)
            ).orNotFound
          )
          .build
          .use(_ => IO.never)
      } yield exitCode
    }
}
