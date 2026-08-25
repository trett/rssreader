package ru.trett.rss.server.controllers

import cats.effect.*
import cats.effect.unsafe.implicits.global
import io.circe.*
import io.circe.syntax.*
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
import ru.trett.rss.server.services.ImportanceService
import org.scalamock.scalatest.MockFactory
import ru.trett.rss.server.repositories.ChannelRepository
import ru.trett.rss.server.repositories.FeedRepository

import java.time.OffsetDateTime
import java.util.concurrent.atomic.AtomicReference

class ChannelControllerSpec extends AnyFunSuite with Matchers with MockFactory {

    /** Records the flags the controller decoded from the query string, so a test can assert on them
      * rather than on the stubbed rows.
      */
    private class RecordingChannelService
        extends ChannelService(
            mock[ChannelRepository],
            mock[FeedRepository],
            mock[Client[IO]],
            new ImportanceService(mock[Client[IO]])
        ) {
        // AtomicReference rather than var: DisableSyntax.noVars is on for test sources too.
        val lastImportantOnly = new AtomicReference[Option[Boolean]](None)
        val lastHideRead = new AtomicReference[Option[Boolean]](None)
        val lastFolder = new AtomicReference[Option[Option[String]]](None)

        override def updateChannelFolder(id: Long, user: User, folder: Option[String]): IO[Int] =
            lastFolder.set(Some(folder))
            IO.pure(1)

        override def getChannelsAndFeeds(
            user: User,
            page: Int,
            limit: Int,
            importantOnly: Boolean,
            hideRead: Boolean
        ): IO[List[FeedItemData]] =
            lastImportantOnly.set(Some(importantOnly))
            lastHideRead.set(Some(hideRead))
            IO.pure(
                List(
                    FeedItemData("1", "test", "test", "test", OffsetDateTime.now(), false, false),
                    FeedItemData("2", "test2", "test2", "test2", OffsetDateTime.now(), true, false)
                )
            )

        override def getChannels(user: User): IO[List[ChannelData]] =
            IO.pure(
                List(ChannelData(1, "test", "test", false), ChannelData(2, "test2", "test2", false))
            )
    }

    private val mockChannelService: ChannelService = new RecordingChannelService
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

    /** `hideRead = true` — the opposite of the default `user`, so between the two the assertions
      * below hold for both values of the setting. Which is the point: the view decides, the setting
      * no longer takes part.
      */
    private val hidingUser =
        User("2", "Hiding User", "hiding@example.com", User.Settings(hideRead = true))

    test("GET /api/channels/feeds without state is unread-only, whatever hideRead says") {
        val service = new RecordingChannelService
        // hideRead = false, and the request must still be unread-only.
        val request =
            AuthedRequest(user, Request[IO](Method.GET, uri"/api/channels/feeds?page=1&limit=2"))
        val response = ChannelController.routes(service).run(request).value.unsafeRunSync().get

        response.status shouldBe Status.Ok
        service.lastHideRead.get shouldBe Some(true)
    }

    test("GET /api/channels/feeds?state=all includes read items, whatever hideRead says") {
        val service = new RecordingChannelService
        // hideRead = true, and the request must still include read items.
        val request = AuthedRequest(
            hidingUser,
            Request[IO](Method.GET, uri"/api/channels/feeds?page=1&limit=2&state=all")
        )
        val response = ChannelController.routes(service).run(request).value.unsafeRunSync().get

        response.status shouldBe Status.Ok
        service.lastHideRead.get shouldBe Some(false)
    }

    test("GET /api/channels/feeds?state=all leaves the important filter alone") {
        val service = new RecordingChannelService
        val request = AuthedRequest(
            user,
            Request[IO](Method.GET, uri"/api/channels/feeds?state=all&filter=important")
        )
        ChannelController.routes(service).run(request).value.unsafeRunSync().get

        service.lastHideRead.get shouldBe Some(false)
        service.lastImportantOnly.get shouldBe Some(true)
    }

    test("GET /api/channels should return all channels") {
        val request = AuthedRequest(user, Request[IO](Method.GET, uri"/api/channels"))
        val response = authedRoutes.run(request).value.unsafeRunSync().get

        response.status shouldBe Status.Ok
        response.as[Json].unsafeRunSync().asArray.get.size shouldBe 2
    }

    test("PUT /api/channels/:id/folder files the channel under a folder") {
        val service = new RecordingChannelService
        val request = AuthedRequest(
            user,
            Request[IO](Method.PUT, uri"/api/channels/1/folder")
                .withEntity("Engineering".asJson)
        )
        val response = ChannelController.routes(service).run(request).value.unsafeRunSync().get

        response.status shouldBe Status.Ok
        service.lastFolder.get shouldBe Some(Some("Engineering"))
    }

    test("PUT /api/channels/:id/folder with null clears the folder") {
        val service = new RecordingChannelService
        val request = AuthedRequest(
            user,
            Request[IO](Method.PUT, uri"/api/channels/1/folder")
                .withEntity(Option.empty[String].asJson)
        )
        val response = ChannelController.routes(service).run(request).value.unsafeRunSync().get

        response.status shouldBe Status.Ok
        service.lastFolder.get shouldBe Some(None)
    }

    test("PUT /api/channels/:id/highlight should update channel highlight status") {
        val mockChannelServiceWithHighlight =
            new ChannelService(
                mock[ChannelRepository],
                mock[FeedRepository],
                mock[Client[IO]],
                new ImportanceService(mock[Client[IO]])
            ) {
                override def updateChannelHighlight(
                    id: Long,
                    user: User,
                    highlighted: Boolean
                ): IO[Int] =
                    IO.pure(1)
            }
        val routes = ChannelController.routes(mockChannelServiceWithHighlight)

        val request = AuthedRequest(
            user,
            Request[IO](Method.PUT, uri"/api/channels/1/highlight")
                .withEntity(true.asJson)
        )
        val response = routes.run(request).value.unsafeRunSync().get

        response.status shouldBe Status.Ok
    }
}
