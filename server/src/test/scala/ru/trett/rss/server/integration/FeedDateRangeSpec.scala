package ru.trett.rss.server.integration

import cats.effect.*
import cats.effect.unsafe.implicits.global
import doobie.hikari.HikariTransactor
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import ru.trett.rss.server.models.{Channel, Feed, User}
import ru.trett.rss.server.repositories.{ChannelRepository, FeedRepository, UserRepository}
import ru.trett.rss.server.utils.TestDatabase

import java.time.OffsetDateTime

/** Integration tests for [[FeedRepository.getFeedsByDateRange]] using Testcontainers PostgreSQL.
  * Backs the MCP `get_news_by_date` tool.
  */
class FeedDateRangeSpec extends AnyFunSuite with Matchers with BeforeAndAfterAll {

    // scalafix:off DisableSyntax.var
    private var transactor: Option[HikariTransactor[IO]] = None
    private var cleanup: Option[IO[Unit]] = None
    private var channelRepository: Option[ChannelRepository] = None
    private var feedRepository: Option[FeedRepository] = None
    // scalafix:on DisableSyntax.var

    private val user = User("date-user", "Date User", "date@example.com", User.Settings())

    private def feed(link: String, date: String, important: Boolean = false): Feed =
        Feed(
            link = link,
            userId = user.id,
            channelId = 0,
            title = s"Item $link",
            description = s"Description $link",
            pubDate = Some(OffsetDateTime.parse(date)),
            isRead = false,
            important = important
        )

    override def beforeAll(): Unit = {
        super.beforeAll()
        val (xa, cleanupIO) = TestDatabase.createTestTransactor().allocated.unsafeRunSync()
        transactor = Some(xa)
        cleanup = Some(cleanupIO)
        channelRepository = Some(new ChannelRepository(xa))
        feedRepository = Some(new FeedRepository(xa))

        val channel = Channel(
            0,
            "Date Channel",
            "https://example.com/date/feed",
            List(
                feed("https://example.com/date/item1", "2026-07-01T10:00:00Z"),
                feed("https://example.com/date/item2", "2026-07-05T10:00:00Z", important = true),
                feed("https://example.com/date/item3", "2026-07-10T10:00:00Z"),
                feed("https://example.com/date/item4", "2026-07-15T10:00:00Z")
            )
        )
        (for {
            _ <- new UserRepository(xa).insertUser(user)
            _ <- channelRepository.get.insertChannel(channel, user)
        } yield ()).unsafeRunSync()
    }

    override def afterAll(): Unit = {
        cleanup.foreach(_.unsafeRunSync())
        super.afterAll()
    }

    private def range(from: String, to: String, limit: Int = 50, importantOnly: Boolean = false) =
        feedRepository.get
            .getFeedsByDateRange(
                user,
                OffsetDateTime.parse(from),
                OffsetDateTime.parse(to),
                limit,
                importantOnly
            )
            .unsafeRunSync()

    test("returns only feeds within the inclusive date range, newest first") {
        val result = range("2026-07-03T00:00:00Z", "2026-07-12T00:00:00Z")
        result.map(_._1.link) shouldBe List(
            "https://example.com/date/item3",
            "https://example.com/date/item2"
        )
        result.map(_._2).distinct shouldBe List("Date Channel")
    }

    test("boundaries are inclusive") {
        val result = range("2026-07-01T10:00:00Z", "2026-07-15T10:00:00Z")
        result should have size 4
    }

    test("importantOnly returns only important feeds") {
        val result = range("2026-07-01T00:00:00Z", "2026-07-20T00:00:00Z", importantOnly = true)
        result.map(_._1.link) shouldBe List("https://example.com/date/item2")
    }

    test("limit caps the number of results, keeping newest") {
        val result = range("2026-07-01T00:00:00Z", "2026-07-20T00:00:00Z", limit = 1)
        result.map(_._1.link) shouldBe List("https://example.com/date/item4")
    }

    test("empty range returns nothing") {
        range("2026-08-01T00:00:00Z", "2026-08-31T00:00:00Z") shouldBe empty
    }
}
