package ru.trett.rss.server.controllers

import cats.effect.*
import cats.effect.unsafe.implicits.global
import org.http4s.*
import org.http4s.implicits.*
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory
import ru.trett.rss.server.config.JobConfig
import ru.trett.rss.server.services.ChannelService
import ru.trett.rss.server.services.ImportanceService
import ru.trett.rss.server.services.UserService
import org.scalamock.scalatest.MockFactory
import org.http4s.headers.Authorization

class JobControllerSpec extends AnyFunSuite with Matchers with MockFactory {

    implicit val loggerFactory: LoggerFactory[IO] = Slf4jFactory.create[IO]

    private def createMocks() = {
        val userService = new UserService(mock[ru.trett.rss.server.repositories.UserRepository]) {
            override def getUsers: IO[List[ru.trett.rss.server.models.User]] = IO.pure(Nil)
        }
        val channelService = new ChannelService(
            mock[ru.trett.rss.server.repositories.ChannelRepository],
            mock[ru.trett.rss.server.repositories.FeedRepository],
            mock[org.http4s.client.Client[IO]],
            new ImportanceService(mock[org.http4s.client.Client[IO]])
        ) {}
        (userService, channelService)
    }

    test("POST /api/jobs/update returns Forbidden if token is empty and no header provided") {
        val (mockUserService, mockChannelService) = createMocks()
        val config = JobConfig("")
        val controller = new JobController(mockChannelService, mockUserService, config)

        val request = Request[IO](Method.POST, uri"/api/jobs/update")
        val response = controller.routes.orNotFound.run(request).unsafeRunSync()

        response.status shouldBe Status.Forbidden
    }

    test("POST /api/jobs/update returns Forbidden if token is configured and header is missing") {
        val (mockUserService, mockChannelService) = createMocks()
        val config = JobConfig("secret")
        val controller = new JobController(mockChannelService, mockUserService, config)

        val request = Request[IO](Method.POST, uri"/api/jobs/update")
        val response = controller.routes.orNotFound.run(request).unsafeRunSync()

        response.status shouldBe Status.Forbidden
    }

    test("POST /api/jobs/update returns Forbidden if token is configured and header is wrong") {
        val (mockUserService, mockChannelService) = createMocks()
        val config = JobConfig("secret")
        val controller = new JobController(mockChannelService, mockUserService, config)

        val request = Request[IO](Method.POST, uri"/api/jobs/update")
            .withHeaders(Authorization(Credentials.Token(AuthScheme.Bearer, "wrong")))
        val response = controller.routes.orNotFound.run(request).unsafeRunSync()

        response.status shouldBe Status.Forbidden
    }

    test("POST /api/jobs/update returns Ok if token is configured and header is correct") {
        val (mockUserService, mockChannelService) = createMocks()
        val config = JobConfig("secret")
        val controller = new JobController(mockChannelService, mockUserService, config)

        val request = Request[IO](Method.POST, uri"/api/jobs/update")
            .withHeaders(Authorization(Credentials.Token(AuthScheme.Bearer, "secret")))
        val response = controller.routes.orNotFound.run(request).unsafeRunSync()

        response.status shouldBe Status.Ok
    }

    test("digest rejects unauthorized requests before executing the job") {
        val (users, channels) = createMocks()
        val controller = new JobController(
            channels,
            users,
            JobConfig("secret"),
            IO.raiseError(new AssertionError("Must not run"))
        )
        List(None, Some("wrong")).foreach { token =>
            val req = Request[IO](Method.POST, uri"/api/jobs/digest")
            val request = token.fold(req)(v =>
                req.withHeaders(Authorization(Credentials.Token(AuthScheme.Bearer, v)))
            )
            controller.routes.orNotFound
                .run(request)
                .unsafeRunSync()
                .status shouldBe Status.Forbidden
        }
    }

    test("digest runs with the update token and reports delivery failures") {
        val (users, channels) = createMocks()
        val req = Request[IO](Method.POST, uri"/api/jobs/digest")
            .withHeaders(Authorization(Credentials.Token(AuthScheme.Bearer, "secret")))
        new JobController(channels, users, JobConfig("secret"), IO.unit).routes.orNotFound
            .run(req)
            .unsafeRunSync()
            .status shouldBe Status.Ok
        new JobController(
            channels,
            users,
            JobConfig("secret"),
            IO.raiseError(new RuntimeException("private"))
        ).routes.orNotFound.run(req).unsafeRunSync().status shouldBe Status.InternalServerError
    }
}
