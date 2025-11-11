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

/** Integration tests for multi-user environment using Testcontainers PostgreSQL.
  *
  * Tests verify that:
  *   - Multiple users can be created
  *   - Data is properly isolated between users
  *   - Channels from one user are not visible to others
  *   - Updates work independently for each user
  *   - Mark as read functionality works independently for each user
  *
  * Note: These tests share a single database instance and run sequentially.
  * Tests are designed to be order-dependent and build upon each other's state
  * to minimize the overhead of database recreation. Each test uses unique
  * identifiers to avoid conflicts where possible.
  */
class MultiUserIntegrationSpec extends AnyFunSuite with Matchers with BeforeAndAfterAll {

    private var transactor: HikariTransactor[IO] = scala.compiletime.uninitialized
    private var cleanup: IO[Unit] = scala.compiletime.uninitialized
    private var userRepository: UserRepository = scala.compiletime.uninitialized
    private var channelRepository: ChannelRepository = scala.compiletime.uninitialized
    private var feedRepository: FeedRepository = scala.compiletime.uninitialized

    private val user1 = User("user1-id", "User One", "user1@example.com", User.Settings())
    private val user2 = User("user2-id", "User Two", "user2@example.com", User.Settings())
    private val user3 =
        User("user3-id", "User Three", "user3@example.com", User.Settings(hideRead = true))

    override def beforeAll(): Unit = {
        super.beforeAll()
        val resource = TestDatabase.createTestTransactor()
        val (xa, cleanupIO) = resource.allocated.unsafeRunSync()
        transactor = xa
        cleanup = cleanupIO
        userRepository = new UserRepository(transactor)
        channelRepository = new ChannelRepository(transactor)
        feedRepository = new FeedRepository(transactor)
    }

    override def afterAll(): Unit = {
        cleanup.unsafeRunSync()
        super.afterAll()
    }

    test("Multiple users can be created") {
        val result = for {
            result1 <- userRepository.insertUser(user1)
            result2 <- userRepository.insertUser(user2)
            result3 <- userRepository.insertUser(user3)
            allUsers <- userRepository.findUsers()
        } yield (result1, result2, result3, allUsers)

        val (r1, r2, r3, users) = result.unsafeRunSync()

        r1.isRight shouldBe true
        r2.isRight shouldBe true
        r3.isRight shouldBe true
        users should have size 3
        users.map(_.id) should contain allOf (user1.id, user2.id, user3.id)
    }

    test("Users can be retrieved by ID") {
        val result = for {
            maybeUser1 <- userRepository.findUserById(user1.id)
            maybeUser2 <- userRepository.findUserById(user2.id)
        } yield (maybeUser1, maybeUser2)

        val (u1, u2) = result.unsafeRunSync()

        u1 shouldBe Some(user1)
        u2 shouldBe Some(user2)
    }

    test("Users can be retrieved by email") {
        val result = for {
            maybeUser1 <- userRepository.findUserByEmail(user1.email)
            maybeUser2 <- userRepository.findUserByEmail(user2.email)
        } yield (maybeUser1, maybeUser2)

        val (u1, u2) = result.unsafeRunSync()

        u1 shouldBe Right(Some(user1))
        u2 shouldBe Right(Some(user2))
    }

    test("Channels are isolated between users") {
        val channel1 = Channel(
            0,
            "User 1 Channel",
            "https://example.com/user1/feed",
            List(
                Feed(
                    "https://example.com/user1/feed/item1",
                    0,
                    "Item 1",
                    "Description 1",
                    Some(OffsetDateTime.now()),
                    false
                )
            )
        )

        val channel2 = Channel(
            0,
            "User 2 Channel",
            "https://example.com/user2/feed",
            List(
                Feed(
                    "https://example.com/user2/feed/item1",
                    0,
                    "Item 1",
                    "Description 1",
                    Some(OffsetDateTime.now()),
                    false
                )
            )
        )

        val result = for {
            channelId1 <- channelRepository.insertChannel(channel1, user1)
            channelId2 <- channelRepository.insertChannel(channel2, user2)
            user1Channels <- channelRepository.findUserChannels(user1)
            user2Channels <- channelRepository.findUserChannels(user2)
        } yield (channelId1, channelId2, user1Channels, user2Channels)

        val (cid1, cid2, u1Channels, u2Channels) = result.unsafeRunSync()

        cid1 should be > 0L
        cid2 should be > 0L
        u1Channels should have size 1
        u2Channels should have size 1
        u1Channels.head.title shouldBe "User 1 Channel"
        u2Channels.head.title shouldBe "User 2 Channel"
        u1Channels.head.id should not be u2Channels.head.id
    }

    test("User cannot see channels from other users") {
        val result = for {
            user1Channels <- channelRepository.findUserChannels(user1)
            user2Channels <- channelRepository.findUserChannels(user2)
            user3Channels <- channelRepository.findUserChannels(user3)
        } yield (user1Channels, user2Channels, user3Channels)

        val (u1Channels, u2Channels, u3Channels) = result.unsafeRunSync()

        u1Channels should have size 1
        u2Channels should have size 1
        u3Channels should have size 0

        u1Channels.map(_.title) should not contain "User 2 Channel"
        u2Channels.map(_.title) should not contain "User 1 Channel"
    }

    test("Feeds are associated with correct users through channels") {
        val result = for {
            user1Feeds <- channelRepository.getChannelsWithFeedsByUser(user1, 10, 0)
            user2Feeds <- channelRepository.getChannelsWithFeedsByUser(user2, 10, 0)
        } yield (user1Feeds, user2Feeds)

        val (u1Feeds, u2Feeds) = result.unsafeRunSync()

        u1Feeds should have size 1
        u2Feeds should have size 1

        val (u1Channel, u1Feed) = u1Feeds.head
        val (u2Channel, u2Feed) = u2Feeds.head

        u1Channel.title shouldBe "User 1 Channel"
        u2Channel.title shouldBe "User 2 Channel"
        u1Feed.link shouldBe "https://example.com/user1/feed/item1"
        u2Feed.link shouldBe "https://example.com/user2/feed/item1"
    }

    test("Mark as read works independently for each user") {
        // Note: Due to the current schema (feeds.link is PK), the same feed cannot exist
        // in multiple channels. This test verifies that marking feeds as read for one user
        // doesn't affect feeds in another user's channels by testing with different feeds.
        // Each user has a different feed in their own channel.
        val user1FeedLink = "https://example.com/user1/feed2/item1"
        val user2FeedLink = "https://example.com/user2/feed2/item1"
        val now = OffsetDateTime.now()

        val channel3 = Channel(
            0,
            "User 1 Second Channel",
            "https://example.com/user1/feed2",
            List(Feed(user1FeedLink, 0, "User 1 Item", "Description", Some(now), false))
        )

        val channel4 = Channel(
            0,
            "User 2 Second Channel",
            "https://example.com/user2/feed2",
            List(Feed(user2FeedLink, 0, "User 2 Item", "Description", Some(now), false))
        )

        val result = for {
            _ <- channelRepository.insertChannel(channel3, user1)
            _ <- channelRepository.insertChannel(channel4, user2)

            // Mark as read for user1 only
            markedCount <- feedRepository.markFeedAsRead(List(user1FeedLink), user1)

            // Get feeds for both users
            user1Feeds <- channelRepository.getChannelsWithFeedsByUser(user1, 20, 0)
            user2Feeds <- channelRepository.getChannelsWithFeedsByUser(user2, 20, 0)
        } yield (markedCount, user1Feeds, user2Feeds)

        val (marked, u1Feeds, u2Feeds) = result.unsafeRunSync()

        marked shouldBe 1 // Only one feed marked for user1

        // Find the feeds for both users
        val user1Feed = u1Feeds.find(_._2.link == user1FeedLink).map(_._2)
        val user2Feed = u2Feeds.find(_._2.link == user2FeedLink).map(_._2)

        user1Feed shouldBe defined
        user2Feed shouldBe defined

        user1Feed.get.isRead shouldBe true // Marked as read for user1
        user2Feed.get.isRead shouldBe false // Still unread for user2
    }

    test("Multiple feeds can be marked as read for a single user") {
        val channel5 = Channel(
            0,
            "User 1 Third Channel",
            "https://example.com/user1/feed3",
            List(
                Feed(
                    "https://example.com/user1/feed3/item1",
                    0,
                    "Item 1",
                    "Desc",
                    Some(OffsetDateTime.now()),
                    false
                ),
                Feed(
                    "https://example.com/user1/feed3/item2",
                    0,
                    "Item 2",
                    "Desc",
                    Some(OffsetDateTime.now()),
                    false
                ),
                Feed(
                    "https://example.com/user1/feed3/item3",
                    0,
                    "Item 3",
                    "Desc",
                    Some(OffsetDateTime.now()),
                    false
                )
            )
        )

        val feedLinks = List(
            "https://example.com/user1/feed3/item1",
            "https://example.com/user1/feed3/item2"
        )

        val result = for {
            _ <- channelRepository.insertChannel(channel5, user1)
            markedCount <- feedRepository.markFeedAsRead(feedLinks, user1)
            user1Feeds <- channelRepository.getChannelsWithFeedsByUser(user1, 50, 0)
        } yield (markedCount, user1Feeds)

        val (marked, feeds) = result.unsafeRunSync()

        marked shouldBe 2

        val channelFeeds =
            feeds.filter(_._1.title == "User 1 Third Channel").map(_._2)

        channelFeeds should have size 3

        val item1 = channelFeeds.find(_.link == feedLinks(0))
        val item2 = channelFeeds.find(_.link == feedLinks(1))
        val item3 =
            channelFeeds.find(_.link == "https://example.com/user1/feed3/item3")

        item1.get.isRead shouldBe true
        item2.get.isRead shouldBe true
        item3.get.isRead shouldBe false
    }

    test("User settings hideRead filters feeds correctly") {
        val channel6 = Channel(
            0,
            "User 3 Channel",
            "https://example.com/user3/feed",
            List(
                Feed(
                    "https://example.com/user3/feed/item1",
                    0,
                    "Item 1",
                    "Desc",
                    Some(OffsetDateTime.now()),
                    false
                ),
                Feed(
                    "https://example.com/user3/feed/item2",
                    0,
                    "Item 2",
                    "Desc",
                    Some(OffsetDateTime.now()),
                    false
                )
            )
        )

        val result = for {
            _ <- channelRepository.insertChannel(channel6, user3)
            _ <- feedRepository.markFeedAsRead(
                List("https://example.com/user3/feed/item1"),
                user3
            )
            // user3 has hideRead = true in settings
            user3Feeds <- channelRepository.getChannelsWithFeedsByUser(user3, 50, 0)
        } yield user3Feeds

        val feeds = result.unsafeRunSync()

        // Should only show unread feeds for user3
        feeds should have size 1
        feeds.head._2.link shouldBe "https://example.com/user3/feed/item2"
        feeds.head._2.isRead shouldBe false
    }

    test("User can only delete their own channels") {
        val channel7 = Channel(
            0,
            "User 2 Third Channel",
            "https://example.com/user2/feed3",
            List()
        )

        val result = for {
            channelId <- channelRepository.insertChannel(channel7, user2)
            user1Channels <- channelRepository.findUserChannels(user1)
            // Try to delete user2's channel as user1
            deleted <- channelRepository.deleteChannel(channelId, user1)
            user2Channels <- channelRepository.findUserChannels(user2)
        } yield (channelId, deleted, user2Channels)

        val (cid, deleted, u2Channels) = result.unsafeRunSync()

        deleted shouldBe 0 // user1 cannot delete user2's channel
        u2Channels.exists(_.id == cid) shouldBe true // Channel still exists for user2
    }

    test("User can delete their own channels") {
        val channel8 = Channel(
            0,
            "User 2 Fourth Channel to Delete",
            "https://example.com/user2/feed4",
            List()
        )

        val result = for {
            channelId <- channelRepository.insertChannel(channel8, user2)
            channelsBeforeDelete <- channelRepository.findUserChannels(user2)
            deleted <- channelRepository.deleteChannel(channelId, user2)
            channelsAfterDelete <- channelRepository.findUserChannels(user2)
        } yield (channelId, deleted, channelsBeforeDelete, channelsAfterDelete)

        val (cid, deleted, before, after) = result.unsafeRunSync()

        deleted shouldBe 1 // Successfully deleted
        before.exists(_.id == cid) shouldBe true
        after.exists(_.id == cid) shouldBe false
    }

    test("Get unread feeds for a specific user") {
        val result = for {
            user1Unread <- feedRepository.getUnreadFeeds(user1)
            user2Unread <- feedRepository.getUnreadFeeds(user2)
        } yield (user1Unread, user2Unread)

        val (u1Unread, u2Unread) = result.unsafeRunSync()

        // Both users should have some unread feeds
        u1Unread should not be empty
        u2Unread should not be empty

        // Verify feeds belong to correct users by checking links
        u1Unread.foreach { feed =>
            feed.link should startWith("https://example.com/user1/")
        }

        u2Unread.foreach { feed =>
            feed.link should startWith("https://example.com/user2/")
        }
    }
}
