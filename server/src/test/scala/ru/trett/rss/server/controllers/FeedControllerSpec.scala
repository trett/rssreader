package ru.trett.rss.server.controllers

import cats.effect.*
import cats.effect.unsafe.implicits.global
import io.circe.*
import io.circe.syntax.*
import org.http4s.*
import org.http4s.circe.*
import org.http4s.circe.CirceEntityDecoder.circeEntityDecoder
import org.http4s.implicits.*
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import ru.trett.rss.server.models.User
import ru.trett.rss.server.services.FeedService
import org.scalamock.scalatest.MockFactory
import ru.trett.rss.server.repositories.FeedRepository

class FeedControllerSpec extends AnyFunSuite with Matchers with MockFactory {

    private val mockFeedService: FeedService =
        new FeedService(mock[FeedRepository]) {
            override def getTotalUnreadCount(userId: String): IO[Int] =
                IO.pure(42)

            override def getUnreadCount(channelId: Long, userId: String): IO[Int] =
                if (channelId == 1L) IO.pure(10)
                else IO.pure(0)

            override def markAsRead(links: List[String], user: User): IO[Int] =
                IO.pure(links.size)
        }

    private val user = User("1", "Test User", "test@example.com", User.Settings())
    private val authedRoutes = FeedController.routes(mockFeedService)

    test("GET /api/feeds/unread/total should return total unread count") {
        val request =
            AuthedRequest(user, Request[IO](Method.GET, uri"/api/feeds/unread/total"))
        val response = authedRoutes.run(request).value.unsafeRunSync().get

        response.status.shouldBe(Status.Ok)
        response.as[Int].unsafeRunSync().shouldBe(42)
    }

    test(
        "GET /api/feeds/channel/{channelId}/unread should return unread count for specific channel"
    ) {
        val request =
            AuthedRequest(user, Request[IO](Method.GET, uri"/api/feeds/channel/1/unread"))
        val response = authedRoutes.run(request).value.unsafeRunSync().get

        response.status.shouldBe(Status.Ok)
        response.as[Int].unsafeRunSync().shouldBe(10)
    }

    test("POST /api/feeds/read should mark feeds as read") {
        val links = List("http://example.com/feed1", "http://example.com/feed2")
        val request = AuthedRequest(
            user,
            Request[IO](Method.POST, uri"/api/feeds/read")
                .withEntity(links.asJson)
        )
        val response = authedRoutes.run(request).value.unsafeRunSync().get

        response.status.shouldBe(Status.Ok)
        response.as[String].unsafeRunSync() should include("2")
    }
}
