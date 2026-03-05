package ru.trett.rss.server.services

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.http4s.client.Client
import org.scalamock.scalatest.MockFactory
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.noop.NoOpFactory
import ru.trett.rss.server.models.User
import ru.trett.rss.server.repositories.FeedRepository

class SummarizeServiceSpec extends AnyFunSuite with Matchers with MockFactory {

    test("streamSummary should always fetch feeds from offset 0 regardless of provided offset") {
        val feedRepository = mock[FeedRepository]
        val client = mock[Client[IO]]
        val user = User("user-id", "User", "user@example.com", User.Settings())

        implicit val loggerFactory: LoggerFactory[IO] = NoOpFactory[IO]

        // Mock getTotalUnreadCount
        (feedRepository.getTotalUnreadCount)
            .expects("user-id")
            .returning(IO.pure(60))

        // This is the CRITICAL part: even if streamSummary is called with offset 30,
        // it MUST call feedRepository.getUnreadFeeds with offset 0 because
        // it's in AI mode and feeds from the previous batch were already marked as read.
        (feedRepository
            .getUnreadFeeds(_: User, _: Int, _: Int))
            .expects(user, 30, 0) // batchSize is 30, expected offset is 0
            .returning(IO.pure(List.empty))

        val service = new SummarizeService(feedRepository, client, "api-key")

        // Call with offset 30 (simulating "Load More" click)
        service.streamSummary(user, 30).compile.toList.unsafeRunSync()
    }
}
