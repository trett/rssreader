package ru.trett.rss.server.controllers

import cats.effect.*
import cats.effect.unsafe.implicits.global
import io.circe.*
import org.http4s.*
import org.http4s.circe.*
import org.http4s.client.Client
import org.http4s.implicits.*
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory
import ru.trett.rss.models.{ChannelData, FeedItemData}
import ru.trett.rss.server.models.User
import ru.trett.rss.server.services.ChannelService
import org.scalamock.scalatest.MockFactory
import ru.trett.rss.server.repositories.ChannelRepository

import java.time.OffsetDateTime

class ChannelControllerSpec extends AnyFunSuite with Matchers with MockFactory {

    private val mockChannelService: ChannelService =
        new ChannelService(mock[ChannelRepository], mock[Client[IO]]) {
            override def getChannelsAndFeeds(
                user: User,
                page: Int,
                limit: Int
            ): IO[List[FeedItemData]] =
                IO.pure(
                    List(
                        FeedItemData("1", "test", "test", "test", OffsetDateTime.now(), false),
                        FeedItemData("2", "test2", "test2", "test2", OffsetDateTime.now(), false)
                    )
                )

            override def getChannels(user: User): IO[List[ChannelData]] =
                IO.pure(List(ChannelData(1, "test", "test"), ChannelData(2, "test2", "test2")))
        }
    private val user = User("1", "Test User", "test@example.com", User.Settings())
    private val authedRoutes = ChannelController.routes(mockChannelService)

    private given LoggerFactory[IO] = Slf4jFactory.create[IO]

    test("GET /api/channels/feeds should return paginated feeds") {
        val request =
            AuthedRequest(user, Request[IO](Method.GET, uri"/api/channels/feeds?page=1&limit=2"))
        val response = authedRoutes.run(request).value.unsafeRunSync().get
        
        response.status shouldBe Status.Ok
        response.as[Json].unsafeRunSync().asArray.get.size shouldBe 2
    }

    test("GET /api/channels should return all channels") {
        val request = AuthedRequest(user, Request[IO](Method.GET, uri"/api/channels"))
        val response = authedRoutes.run(request).value.unsafeRunSync().get

        response.status shouldBe Status.Ok
        response.as[Json].unsafeRunSync().asArray.get.size shouldBe 2
    }
}
