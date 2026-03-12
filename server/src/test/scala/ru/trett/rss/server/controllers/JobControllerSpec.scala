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
            mock[org.http4s.client.Client[IO]]
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
}
