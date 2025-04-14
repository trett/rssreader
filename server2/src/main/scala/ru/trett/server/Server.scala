package ru.trett.server

import cats.data.OptionT
import cats.effect.*
import cats.implicits.*
import com.comcast.ip4s.*
import com.zaxxer.hikari.HikariConfig
import doobie.hikari.*
import doobie.util.log.LogEvent
import doobie.util.log.LogHandler
import org.http4s.AuthedRoutes
import org.http4s.HttpRoutes
import org.http4s.Uri
import org.http4s.ember.server.*
import org.http4s.implicits.*
import org.http4s.server.middleware.CORS
import org.http4s.server.middleware.CORSPolicy
import org.http4s.server.middleware.ErrorAction
import org.http4s.server.middleware.ErrorHandling
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
import ru.trett.server.controllers.FeedController
import ru.trett.server.controllers.LoginController
import ru.trett.server.controllers.UserController
import ru.trett.server.db.FlywayMigration
import ru.trett.server.models.User
import ru.trett.server.repositories.ChannelRepository
import ru.trett.server.repositories.FeedRepository
import ru.trett.server.repositories.UserRepository
import ru.trett.server.services.ChannelService
import ru.trett.server.services.FeedService
import ru.trett.server.services.UserService

object Server extends IOApp:

    given LoggerFactory[IO] = Slf4jFactory[IO]

    private val logger: SelfAwareStructuredLogger[IO] =
        LoggerFactory[IO].getLogger

    private def loadConfig: Option[AppConfig] =
        ConfigSource.default.load[AppConfig] match {
            case Right(config) => Some(config)
            case Left(error)   => None
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
            xa <- HikariTransactor
                .fromHikariConfig[IO](hikariConfig, logHandler = Some(withSqlLogHandler))
        } yield xa

    private def createCorsPolicy(config: CorsConfig): CORSPolicy =
        CORS.policy
            .withAllowOriginHost(_ == Uri.fromString(config.allowedOrigin))
            .withAllowCredentials(config.allowCredentials)
            .withMaxAge(config.maxAge)

    private def unprotectedRoutes(
        sessionManager: SessionManager[IO],
        oauthConfig: OAuthConfig,
        userService: UserService
    ): HttpRoutes[IO] =
        LoginController.routes(sessionManager, oauthConfig, userService)

    private def authedRoutes(
        channelService: ChannelService,
        userService: UserService,
        feedService: FeedService
    ): AuthedRoutes[User, IO] =
        ChannelController.routes(channelService)
            <+> UserController.routes(userService)
            <+> FeedController.routes(feedService)

    private def routes(
        sessionManager: SessionManager[IO],
        channelService: ChannelService,
        userService: UserService,
        feedService: FeedService,
        oauthConfig: OAuthConfig
    ): HttpRoutes[IO] =
        unprotectedRoutes(sessionManager, oauthConfig, userService) <+>
            AuthFilter.middleware(sessionManager, userService)(
                authedRoutes(channelService, userService, feedService)
            )

    private def errorHandler(t: Throwable, msg: => String): OptionT[IO, Unit] =
        OptionT.liftF(
            IO.println(msg) >>
                IO(t.printStackTrace())
        )

    private def withErrorLogging(routes: HttpRoutes[IO]) =
        ErrorHandling.Recover.total(
            ErrorAction.log(
                routes,
                messageFailureLogAction = errorHandler,
                serviceErrorLogAction = errorHandler
            )
        )

    private val withSqlLogHandler: LogHandler[IO] = new LogHandler[IO] {
        def run(logEvent: LogEvent): IO[Unit] =
            IO {
                println(logEvent.sql)
            }
    }

    override def run(args: List[String]): IO[ExitCode] =
        val appConfig = loadConfig match {
            case Some(config) => config
            case None =>
                println("Failed to load configuration")
                return IO.pure(ExitCode.Error)
        }
        transactor(appConfig.db).use { xa =>
            for {
                _ <- FlywayMigration.migrate(appConfig.db)
                corsPolicy = createCorsPolicy(appConfig.cors)
                sessionManager <- SessionManager.create[IO]
                channelRepository = ChannelRepository(xa)
                feedRepository = FeedRepository(xa)
                feedService = FeedService(feedRepository)
                userRepository = UserRepository(xa)
                userService = UserService(userRepository)
                channelService = ChannelService(channelRepository)
                _ <- logger.info("Starting server on port: " + appConfig.server.port)
                exitCode <- EmberServerBuilder
                    .default[IO]
                    .withHost(ipv4"0.0.0.0")
                    .withPort(Port.fromInt(appConfig.server.port).get)
                    .withHttpApp(
                        withErrorLogging(
                            corsPolicy(
                                routes(
                                    sessionManager,
                                    channelService,
                                    userService,
                                    feedService,
                                    appConfig.oauth
                                )
                            )
                        ).orNotFound
                    )
                    .build
                    .use(_ => IO.never)
            } yield exitCode
        }
