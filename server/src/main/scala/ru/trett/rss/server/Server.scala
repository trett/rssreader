package ru.trett.rss.server

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
import org.http4s.StaticFile
import org.http4s.Uri
import org.http4s.client.Client
import org.http4s.dsl.io.*
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.*
import org.http4s.implicits.*
import org.http4s.server.middleware.CORS
import org.http4s.server.middleware.CORSPolicy
import org.http4s.server.middleware.ErrorAction
import org.http4s.server.middleware.ErrorHandling
import org.http4s.server.staticcontent.*
import org.typelevel.log4cats.*
import org.typelevel.log4cats.slf4j.*
import pureconfig.ConfigSource
import ru.trett.rss.server.authorization.AuthFilter
import ru.trett.rss.server.authorization.SessionManager
import ru.trett.rss.server.config.AppConfig
import ru.trett.rss.server.config.CorsConfig
import ru.trett.rss.server.config.DbConfig
import ru.trett.rss.server.config.OAuthConfig
import ru.trett.rss.server.controllers.ChannelController
import ru.trett.rss.server.controllers.FeedController
import ru.trett.rss.server.controllers.LoginController
import ru.trett.rss.server.controllers.LogoutController
import ru.trett.rss.server.controllers.SummarizeController
import ru.trett.rss.server.controllers.UserController
import ru.trett.rss.server.db.FlywayMigration
import ru.trett.rss.server.models.User
import ru.trett.rss.server.repositories.ChannelRepository
import ru.trett.rss.server.repositories.FeedRepository
import ru.trett.rss.server.repositories.UserRepository
import ru.trett.rss.server.services.ChannelService
import ru.trett.rss.server.services.FeedService
import ru.trett.rss.server.services.SummarizeService
import ru.trett.rss.server.services.UpdateTask
import ru.trett.rss.server.services.UserService

object Server extends IOApp:

    private val logger: SelfAwareStructuredLogger[IO] =
        LoggerFactory[IO].getLogger

    private val withSqlLogHandler: LogHandler[IO] = (logEvent: LogEvent) =>
        IO {
            println(logEvent.sql)
        }

    override def run(args: List[String]): IO[ExitCode] =
        val appConfig = loadConfig match {
            case Some(config) => config
            case None =>
                println("Failed to load configuration")
                return IO.pure(ExitCode.Error)
        }

        val client = EmberClientBuilder
            .default[IO]
            .build
        transactor(appConfig.db).use { xa =>
            client.use { client =>
                for {
                    _ <- FlywayMigration.migrate(appConfig.db)
                    corsPolicy = createCorsPolicy(appConfig.cors)
                    sessionManager <- SessionManager[IO]
                    channelRepository = ChannelRepository(xa)
                    feedRepository = FeedRepository(xa)
                    feedService = FeedService(feedRepository)
                    userRepository = UserRepository(xa)
                    userService = UserService(userRepository)
                    summarizeService = new SummarizeService(
                        feedRepository,
                        client,
                        appConfig.google.apiKey
                    )
                    channelService = ChannelService(channelRepository, client)
                    _ <- logger.info("Starting server on port: " + appConfig.server.port)
                    exitCode <- UpdateTask(channelService, userService).background.void.surround {
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
                                                authFilter,
                                                client,
                                                summarizeService,
                                                new LogoutController[IO](sessionManager)
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
                hikariConfig.setMaximumPoolSize(32)
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
        authFilter: AuthFilter[IO],
        client: Client[IO],
        summarizeService: SummarizeService,
        logoutController: LogoutController[IO]
    ): HttpRoutes[IO] =
        unprotectedRoutes(sessionManager, oauthConfig, userService, client) <+>
            authFilter.middleware(sessionManager, userService)(
                authedRoutes(
                    channelService,
                    userService,
                    feedService,
                    summarizeService,
                    user => authFilter.updateCache(user),
                    logoutController
                )
            )
    private def resourceRoutes: HttpRoutes[IO] =
        val indexRoute = HttpRoutes.of[IO] {
            case request @ GET -> Root =>
                StaticFile.fromResource("/public/index.html", Some(request)).getOrElseF(NotFound())
        }
        indexRoute <+> resourceServiceBuilder[IO]("/public").toRoutes

    private def unprotectedRoutes(
        sessionManager: SessionManager[IO],
        oauthConfig: OAuthConfig,
        userService: UserService,
        client: Client[IO]
    ): HttpRoutes[IO] =
        LoginController.routes(sessionManager, oauthConfig, userService, client) <+> resourceRoutes

    private def authedRoutes(
        channelService: ChannelService,
        userService: UserService,
        feedService: FeedService,
        summarizeService: SummarizeService,
        cacheUpdater: User => IO[Unit],
        logoutController: LogoutController[IO]
    ): AuthedRoutes[User, IO] =
        ChannelController.routes(channelService)
            <+> UserController.routes(userService, cacheUpdater)
            <+> FeedController.routes(feedService)
            <+> SummarizeController.routes(summarizeService)
            <+> logoutController.routes

    private def withErrorLogging(routes: HttpRoutes[IO]) =
        ErrorHandling.Recover.total(
            ErrorAction.log(
                routes,
                messageFailureLogAction = errorHandler,
                serviceErrorLogAction = errorHandler
            )
        )

    private def errorHandler(t: Throwable, msg: => String): OptionT[IO, Unit] =
        OptionT.liftF(logger.error(t)(msg))
