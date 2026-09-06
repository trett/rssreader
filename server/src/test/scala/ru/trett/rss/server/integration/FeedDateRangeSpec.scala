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

/** Integration tests for [[FeedRepository.getFeedsByDateRangeAllChannels]] using Testcontainers
  * PostgreSQL. Backs the MCP `get_news_by_date` tool: all channels in one query, limited to items
  * that are important or belong to a highlighted channel, capped per channel.
  */
class FeedDateRangeSpec extends AnyFunSuite with Matchers with BeforeAndAfterAll {

    // scalafix:off DisableSyntax.var
    private var transactor: Option[HikariTransactor[IO]] = None
    private var cleanup: Option[IO[Unit]] = None
    private var feedRepository: Option[FeedRepository] = None
    private var plainChannelId: Long = 0
    private var highlightedChannelId: Long = 0
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
        val channelRepository = new ChannelRepository(xa)
        feedRepository = Some(new FeedRepository(xa))

        // Not highlighted: only its `important` items should surface.
        val plainChannel = Channel(
            0,
            "Plain Channel",
            "https://example.com/plain/feed",
            List(
                feed("https://example.com/plain/item1", "2026-07-01T10:00:00Z"),
                feed("https://example.com/plain/item2", "2026-07-05T10:00:00Z", important = true),
                feed("https://example.com/plain/item3", "2026-07-10T10:00:00Z", important = true),
                feed("https://example.com/plain/item4", "2026-07-15T10:00:00Z")
            )
        )
        // Highlighted: all of its items should surface regardless of importance.
        val highlightedChannel = Channel(
            0,
            "Highlighted Channel",
            "https://example.com/highlighted/feed",
            List(
                feed("https://example.com/highlighted/item1", "2026-07-05T10:00:00Z"),
                feed("https://example.com/highlighted/item2", "2026-07-06T10:00:00Z")
            )
        )
        (for {
            _ <- new UserRepository(xa).insertUser(user)
            plainId <- channelRepository.insertChannel(plainChannel, user)
            highlightedId <- channelRepository.insertChannel(highlightedChannel, user)
            _ <- channelRepository.updateChannelHighlight(highlightedId, user, highlighted = true)
        } yield {
            plainChannelId = plainId
            highlightedChannelId = highlightedId
        }).unsafeRunSync()
    }

    override def afterAll(): Unit = {
        cleanup.foreach(_.unsafeRunSync())
        super.afterAll()
    }

    private def allChannels(from: String, to: String, limitPerChannel: Int = 50) =
        feedRepository.get
            .getFeedsByDateRangeAllChannels(
                user,
                OffsetDateTime.parse(from),
                OffsetDateTime.parse(to),
                limitPerChannel
            )
            .unsafeRunSync()

    private def channelItems(channelId: Long, from: String, to: String, limitPerChannel: Int = 50) =
        allChannels(from, to, limitPerChannel).filter(_._1.channelId == channelId)

    test("returns only a channel's important items within the range, newest first") {
        val result = channelItems(plainChannelId, "2026-07-01T00:00:00Z", "2026-07-20T00:00:00Z")
        result.map(_._1.link) shouldBe List(
            "https://example.com/plain/item3",
            "https://example.com/plain/item2"
        )
        result.map(_._2).distinct shouldBe List("Plain Channel")
    }

    test("a highlighted channel returns all its items regardless of importance") {
        val result =
            channelItems(highlightedChannelId, "2026-07-01T00:00:00Z", "2026-07-20T00:00:00Z")
        result.map(_._1.link) shouldBe List(
            "https://example.com/highlighted/item2",
            "https://example.com/highlighted/item1"
        )
    }

    test("boundaries are inclusive") {
        val result = channelItems(plainChannelId, "2026-07-05T10:00:00Z", "2026-07-10T10:00:00Z")
        result.map(_._1.link) shouldBe List(
            "https://example.com/plain/item3",
            "https://example.com/plain/item2"
        )
    }

    test("empty range returns nothing") {
        allChannels("2026-08-01T00:00:00Z", "2026-08-31T00:00:00Z") shouldBe empty
    }

    test("all-channels query spans every channel, applying the important/highlighted filter") {
        val result = allChannels("2026-07-01T00:00:00Z", "2026-07-20T00:00:00Z")
        result.map(_._1.link).toSet shouldBe Set(
            "https://example.com/plain/item2",
            "https://example.com/plain/item3",
            "https://example.com/highlighted/item1",
            "https://example.com/highlighted/item2"
        )
        result.map(_._1.channelId).toSet shouldBe Set(plainChannelId, highlightedChannelId)
    }

    test("all-channels limit is applied per channel, keeping newest") {
        val result =
            allChannels("2026-07-01T00:00:00Z", "2026-07-20T00:00:00Z", limitPerChannel = 1)
        result.groupBy(_._1.channelId).view.mapValues(_.size).toMap shouldBe Map(
            plainChannelId -> 1,
            highlightedChannelId -> 1
        )
        result.map(_._1.link).toSet shouldBe Set(
            "https://example.com/plain/item3",
            "https://example.com/highlighted/item2"
        )
    }

    test("channel unread count respects important filter for plain and highlighted channels") {
        val repo = feedRepository.get
        repo.getUnreadCount(plainChannelId, user.id, importantOnly = false)
            .unsafeRunSync() shouldBe 4
        repo.getUnreadCount(plainChannelId, user.id, importantOnly = true)
            .unsafeRunSync() shouldBe 2
        repo.getUnreadCount(highlightedChannelId, user.id, importantOnly = false)
            .unsafeRunSync() shouldBe 2
        repo.getUnreadCount(highlightedChannelId, user.id, importantOnly = true)
            .unsafeRunSync() shouldBe 2
    }

    test("total unread count respects important filter including highlighted channels") {
        val repo = feedRepository.get
        repo.getTotalUnreadCount(user.id, importantOnly = false).unsafeRunSync() shouldBe 6
        repo.getTotalUnreadCount(user.id, importantOnly = true).unsafeRunSync() shouldBe 4
    }
}
