package ru.trett.rss.server.controllers

import cats.effect.{IO, Ref}
import cats.effect.unsafe.implicits.global
import io.circe.parser.decode
import org.http4s.*
import org.http4s.implicits.*
import org.http4s.headers.`Content-Type`
import org.scalamock.scalatest.MockFactory
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory
import ru.trett.rss.server.models.User
import ru.trett.rss.server.repositories.UserRepository
import ru.trett.rss.server.services.UserService

class UserSettingsDigestSpec extends AnyFunSuite with Matchers with MockFactory:
    given LoggerFactory[IO] = Slf4jFactory.create[IO]
    private val user =
        User("u", "User", "login@example.com", User.Settings(mcpClientSecret = Some("existing")))

    test("old stored settings decode without a digest address") {
        decode[User.Settings]("""{"hideRead":true}""").toOption.get.digestEmail shouldBe None
    }

    test(
        "saves and clears digest email while preserving server-managed settings and updating cache"
    ) {
        val saved = Ref.of[IO, List[User]](Nil).unsafeRunSync()
        val cached = Ref.of[IO, List[User]](Nil).unsafeRunSync()
        val service = new UserService(mock[UserRepository]):
            override def updateUserSettings(updated: User) = saved.update(_ :+ updated).as(1)
        val routes = UserController.routes(service, updated => cached.update(_ :+ updated))
        List("\" reader@gmail.com \"", "\"\"").foreach { address =>
            val request = Request[IO](Method.POST, uri"/api/user/settings")
                .withEntity(
                    s"""{"name":"User","hideRead":false,"bannedCategories":[],"keywordRules":[],"filterNews":false,"digestEmail":$address}"""
                )
                .putHeaders(`Content-Type`(MediaType.application.json))
            routes
                .run(AuthedRequest(user, request))
                .value
                .unsafeRunSync()
                .get
                .status shouldBe Status.Ok
        }
        val users = saved.get.unsafeRunSync()
        users.map(_.settings.digestEmail) shouldBe List(Some("reader@gmail.com"), None)
        users.foreach(_.settings.mcpClientSecret shouldBe Some("existing"))
        cached.get.unsafeRunSync() shouldBe users
    }
