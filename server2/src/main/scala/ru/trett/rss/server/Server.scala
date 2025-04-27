package ru.trett.rss.server

import cats.data.OptionT
import cats.effect.*
import cats.implicits.*
import com.comcast.ip4s.*
import com.zaxxer.hikari.HikariConfig
import doobie.hikari.*
import doobie.util.log.{LogEvent, LogHandler}
import org.http4s.ember.server.*
import org.http4s.implicits.*
import org.http4s.server.middleware.{CORS, CORSPolicy, ErrorAction, ErrorHandling}
import org.http4s.{AuthedRoutes, HttpRoutes, Uri}
import org.typelevel.log4cats.*
import org.typelevel.log4cats.slf4j.*
import pureconfig.ConfigSource
import ru.trett.rss.server.authorization.{AuthFilter, SessionManager}
import ru.trett.rss.server.config.{AppConfig, CorsConfig, DbConfig, OAuthConfig}
import ru.trett.rss.server.controllers.{
    ChannelController,
    FeedController,
    LoginController,
    UserController
}
import ru.trett.rss.server.db.FlywayMigration
import ru.trett.rss.server.models.User
import ru.trett.rss.server.repositories.{ChannelRepository, FeedRepository, UserRepository}
import ru.trett.rss.server.services.{ChannelService, FeedService, UpdateTask, UserService}

object Server extends IOApp:

    private val logger: SelfAwareStructuredLogger[IO] =
        LoggerFactory[IO].getLogger

    private val withSqlLogHandler: LogHandler[IO] = (logEvent: LogEvent) =>
        IO {
            println(logEvent.sql)
        }

    given LoggerFactory[IO] = Slf4jFactory[IO]

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
                sessionManager <- SessionManager[IO]
                channelRepository = ChannelRepository(xa)
                feedRepository = FeedRepository(xa)
                feedService = FeedService(feedRepository)
                userRepository = UserRepository(xa)
                userService = UserService(userRepository)
                channelService = ChannelService(channelRepository)
                _ <- logger.info("Starting server on port: " + appConfig.server.port)
                exitCode <- UpdateTask(channelService, userService).background.use { task =>
                    for {
                        authFilter <- AuthFilter[IO]
                        server <- EmberServerBuilder
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
                                            appConfig.oauth,
                                            authFilter
                                        )
                                    )
                                ).orNotFound
                            )
                            .build
                            .use(_ => IO.never)
                    } yield server
                }
            } yield exitCode
        }

    private def loadConfig: Option[AppConfig] =
        ConfigSource.default.load[AppConfig] match {
            case Right(config) => Some(config)
            case Left(err) =>
                println(s"Failed to load configuration: $err")
                None
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
                .fromHikariConfig[IO](
                    hikariConfig
                    /** , logHandler = Some(withSqlLogHandler) * */
                )
        } yield xa

    private def createCorsPolicy(config: CorsConfig): CORSPolicy =
        CORS.policy
            .withAllowOriginHost(_ == Uri.fromString(config.allowedOrigin))
            .withAllowCredentials(config.allowCredentials)
            .withMaxAge(config.maxAge)

    private def routes(
        sessionManager: SessionManager[IO],
        channelService: ChannelService,
        userService: UserService,
        feedService: FeedService,
        oauthConfig: OAuthConfig,
        authFilter: AuthFilter[IO]
    ): HttpRoutes[IO] =
        unprotectedRoutes(sessionManager, oauthConfig, userService) <+>
            authFilter.middleware(sessionManager, userService)(
                authedRoutes(channelService, userService, feedService)
            )

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

    private def withErrorLogging(routes: HttpRoutes[IO]) =
        ErrorHandling.Recover.total(
            ErrorAction.log(
                routes,
                messageFailureLogAction = errorHandler,
                serviceErrorLogAction = errorHandler
            )
        )

    private def errorHandler(t: Throwable, msg: => String): OptionT[IO, Unit] =
        OptionT.liftF(
            IO.println(msg) >>
                IO(t.printStackTrace())
        )
