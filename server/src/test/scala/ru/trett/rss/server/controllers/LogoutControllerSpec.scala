package ru.trett.rss.server.controllers

import cats.effect.*
import cats.effect.unsafe.implicits.global
import org.http4s.*
import org.http4s.implicits.*
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory
import ru.trett.rss.server.models.User

import org.typelevel.ci.*

class LogoutControllerSpec extends AnyFunSuite with Matchers {

    given LoggerFactory[IO] = Slf4jFactory.create[IO]

    private val user = User("1", "Test User", "test@example.com", User.Settings())
    private val controller = new LogoutController[IO]

    test("POST /api/logout clears sessionId cookie with SameSite.Lax and negative maxAge") {
        val request = AuthedRequest(user, Request[IO](Method.POST, uri"/api/logout"))
        val response = controller.routes.run(request).value.unsafeRunSync().get

        response.status.shouldBe(Status.Ok)
        val setCookieHeader = response.headers.get(ci"Set-Cookie")
        setCookieHeader.shouldBe(defined)
        val cookieStr = setCookieHeader.get.head.value
        cookieStr.should(include("sessionId="))
        cookieStr.should(include("Max-Age=-1"))
        cookieStr.should(include("Path=/"))
        cookieStr.should(include("SameSite=Lax"))
        cookieStr.should(include("Secure"))
        cookieStr.should(include("HttpOnly"))
    }
}
