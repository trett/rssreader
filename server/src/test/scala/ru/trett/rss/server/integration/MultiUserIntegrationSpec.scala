package ru.trett.rss.server.integration

import cats.effect.*
import cats.effect.unsafe.implicits.global
import cats.implicits.*
import doobie.*
import doobie.implicits.*
import doobie.hikari.HikariTransactor
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
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
  * Each test runs in isolation with a clean database state to ensure tests can run independently
  * and in any order.
  */
class MultiUserIntegrationSpec
    extends AnyFunSuite
    with Matchers
    with BeforeAndAfterAll
    with BeforeAndAfterEach {

    // scalafix:off DisableSyntax.var
    private var transactor: Option[HikariTransactor[IO]] = None
    private var cleanup: Option[IO[Unit]] = None
    private var userRepository: Option[UserRepository] = None
    private var channelRepository: Option[ChannelRepository] = None
    private var feedRepository: Option[FeedRepository] = None
    // scalafix:on DisableSyntax.var

    private val user1 = User("user1-id", "User One", "user1@example.com", User.Settings())
    private val user2 = User("user2-id", "User Two", "user2@example.com", User.Settings())
    private val user3 =
        User("user3-id", "User Three", "user3@example.com", User.Settings(hideRead = true))

    override def beforeAll(): Unit = {
        super.beforeAll()
        val resource = TestDatabase.createTestTransactor()
        val (xa, cleanupIO) = resource.allocated.unsafeRunSync()
        transactor = Some(xa)
        cleanup = Some(cleanupIO)
        userRepository = Some(new UserRepository(xa))
        channelRepository = Some(new ChannelRepository(xa))
        feedRepository = Some(new FeedRepository(xa))
    }

    override def afterAll(): Unit = {
        cleanup.foreach(_.unsafeRunSync())
        super.afterAll()
    }

    override def beforeEach(): Unit = {
        super.beforeEach()
        // Clean up database state before each test for isolation
        cleanDatabase().unsafeRunSync()
    }

    private def cleanDatabase(): IO[Unit] =
        val deleteFeeds = sql"DELETE FROM feeds".update.run
        val deleteChannels = sql"DELETE FROM channels".update.run
        val deleteUserChannels = sql"DELETE FROM user_channels".update.run
        val deleteUsers = sql"DELETE FROM users".update.run

        (for {
            _ <- deleteFeeds
            _ <- deleteUserChannels
            _ <- deleteChannels
            _ <- deleteUsers
        } yield ()).transact(transactor.get)

    // Helper method to set up users for tests
    private def setupUsers(users: User*): IO[Unit] =
        users.toList.traverse_(user => userRepository.get.insertUser(user).void)

    test("Multiple users can be created") {
        val result = for {
            result1 <- userRepository.get.insertUser(user1)
            result2 <- userRepository.get.insertUser(user2)
            result3 <- userRepository.get.insertUser(user3)
            allUsers <- userRepository.get.findUsers()
        } yield (result1, result2, result3, allUsers)

        val (r1, r2, r3, users) = result.unsafeRunSync()

        r1.isRight shouldBe true
        r2.isRight shouldBe true
        r3.isRight shouldBe true
        users should have size 3
        (users.map(_.id) should contain).allOf(user1.id, user2.id, user3.id)
    }

    test("Users can be retrieved by ID") {
        val result = for {
            _ <- setupUsers(user1, user2)
            maybeUser1 <- userRepository.get.findUserById(user1.id)
            maybeUser2 <- userRepository.get.findUserById(user2.id)
        } yield (maybeUser1, maybeUser2)

        val (u1, u2) = result.unsafeRunSync()

        u1 shouldBe Some(user1)
        u2 shouldBe Some(user2)
    }

    test("Users can be retrieved by email") {
        val result = for {
            _ <- setupUsers(user1, user2)
            maybeUser1 <- userRepository.get.findUserByEmail(user1.email)
            maybeUser2 <- userRepository.get.findUserByEmail(user2.email)
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
                    link = "https://example.com/user1/feed/item1",
                    userId = user1.id,
                    channelId = 0,
                    title = "Item 1",
                    description = "Description 1",
                    pubDate = Some(OffsetDateTime.now()),
                    isRead = false
                )
            )
        )

        val channel2 = Channel(
            0,
            "User 2 Channel",
            "https://example.com/user2/feed",
            List(
                Feed(
                    link = "https://example.com/user2/feed/item1",
                    userId = user2.id,
                    channelId = 0,
                    title = "Item 1",
                    description = "Description 1",
                    pubDate = Some(OffsetDateTime.now()),
                    isRead = false
                )
            )
        )

        val result = for {
            _ <- setupUsers(user1, user2)
            channelId1 <- channelRepository.get.insertChannel(channel1, user1)
            channelId2 <- channelRepository.get.insertChannel(channel2, user2)
            user1Channels <- channelRepository.get.findUserChannels(user1)
            user2Channels <- channelRepository.get.findUserChannels(user2)
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
        val channel1 = Channel(
            0,
            "User 1 Channel",
            "https://example.com/user1/feed",
            List(
                Feed(
                    link = "https://example.com/user1/feed/item1",
                    userId = user1.id,
                    channelId = 0,
                    title = "Item 1",
                    description = "Description 1",
                    pubDate = Some(OffsetDateTime.now()),
                    isRead = false
                )
            )
        )

        val result = for {
            _ <- setupUsers(user1, user2, user3)
            _ <- channelRepository.get.insertChannel(channel1, user1)
            user1Channels <- channelRepository.get.findUserChannels(user1)
            user2Channels <- channelRepository.get.findUserChannels(user2)
            user3Channels <- channelRepository.get.findUserChannels(user3)
        } yield (user1Channels, user2Channels, user3Channels)

        val (u1Channels, u2Channels, u3Channels) = result.unsafeRunSync()

        u1Channels should have size 1
        u2Channels should have size 0
        u3Channels should have size 0

        u1Channels.map(_.title) should not contain "User 2 Channel"
    }

    test("Feeds are associated with correct users through channels") {
        val channel1 = Channel(
            0,
            "User 1 Channel",
            "https://example.com/user1/feed",
            List(
                Feed(
                    link = "https://example.com/user1/feed/item1",
                    userId = user1.id,
                    channelId = 0,
                    title = "Item 1",
                    description = "Description 1",
                    pubDate = Some(OffsetDateTime.now()),
                    isRead = false
                )
            )
        )

        val channel2 = Channel(
            0,
            "User 2 Channel",
            "https://example.com/user2/feed",
            List(
                Feed(
                    link = "https://example.com/user2/feed/item1",
                    userId = user2.id,
                    channelId = 0,
                    title = "Item 1",
                    description = "Description 1",
                    pubDate = Some(OffsetDateTime.now()),
                    isRead = false
                )
            )
        )

        val result = for {
            _ <- setupUsers(user1, user2)
            _ <- channelRepository.get.insertChannel(channel1, user1)
            _ <- channelRepository.get.insertChannel(channel2, user2)
            user1Feeds <- channelRepository.get.getChannelsWithFeedsByUser(user1, 10, 0)
            user2Feeds <- channelRepository.get.getChannelsWithFeedsByUser(user2, 10, 0)
        } yield (user1Feeds, user2Feeds)

        val (u1Feeds, u2Feeds) = result.unsafeRunSync()

        u1Feeds should have size 1
        u2Feeds should have size 1

        val (u1Channel, u1Feed, _) = u1Feeds.head
        val (u2Channel, u2Feed, _) = u2Feeds.head

        u1Channel.title shouldBe "User 1 Channel"
        u2Channel.title shouldBe "User 2 Channel"
        u1Feed.link shouldBe "https://example.com/user1/feed/item1"
        u2Feed.link shouldBe "https://example.com/user2/feed/item1"
    }

    test("Mark as read works independently for each user") {
        // With the updated schema (composite PK on link, user_id), we can now test
        // per-user read states. This test verifies that marking feeds as read for one user
        // doesn't affect the same feed in another user's view.
        val user1FeedLink = "https://example.com/user1/feed2/item1"
        val user2FeedLink = "https://example.com/user2/feed2/item1"
        val now = OffsetDateTime.now()

        val channel3 = Channel(
            0,
            "User 1 Second Channel",
            "https://example.com/user1/feed2",
            List(Feed(user1FeedLink, user1.id, 0, "User 1 Item", "Description", Some(now), false))
        )

        val channel4 = Channel(
            0,
            "User 2 Second Channel",
            "https://example.com/user2/feed2",
            List(Feed(user2FeedLink, user2.id, 0, "User 2 Item", "Description", Some(now), false))
        )

        val result = for {
            _ <- setupUsers(user1, user2)
            _ <- channelRepository.get.insertChannel(channel3, user1)
            _ <- channelRepository.get.insertChannel(channel4, user2)

            // Mark as read for user1 only
            markedCount <- feedRepository.get.markFeedAsRead(List(user1FeedLink), user1)

            // Get feeds for both users
            user1Feeds <- channelRepository.get.getChannelsWithFeedsByUser(user1, 20, 0)
            user2Feeds <- channelRepository.get.getChannelsWithFeedsByUser(user2, 20, 0)
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

    test("Same feed link can have independent read states per user") {
        // This test verifies the new schema allows the same feed URL to exist
        // for multiple users with independent read states
        val sharedFeedLink = "https://example.com/shared/feed/item"
        val now = OffsetDateTime.now()

        val channel3a = Channel(
            0,
            "User 1 Shared Feed Channel",
            "https://example.com/user1/feed_shared",
            List(Feed(sharedFeedLink, user1.id, 0, "Shared Item", "Description", Some(now), false))
        )

        val channel3b = Channel(
            0,
            "User 2 Shared Feed Channel",
            "https://example.com/user2/feed_shared",
            List(Feed(sharedFeedLink, user2.id, 0, "Shared Item", "Description", Some(now), false))
        )

        val result = for {
            _ <- setupUsers(user1, user2)
            _ <- channelRepository.get.insertChannel(channel3a, user1)
            _ <- channelRepository.get.insertChannel(channel3b, user2)

            // Mark as read for user1 only
            markedCount <- feedRepository.get.markFeedAsRead(List(sharedFeedLink), user1)

            // Get feeds for both users
            user1Feeds <- channelRepository.get.getChannelsWithFeedsByUser(user1, 50, 0)
            user2Feeds <- channelRepository.get.getChannelsWithFeedsByUser(user2, 50, 0)
        } yield (markedCount, user1Feeds, user2Feeds)

        val (marked, u1Feeds, u2Feeds) = result.unsafeRunSync()

        marked shouldBe 1 // Only one feed marked for user1

        // Find the shared feed for both users
        val user1SharedFeed = u1Feeds.find(_._2.link == sharedFeedLink).map(_._2)
        val user2SharedFeed = u2Feeds.find(_._2.link == sharedFeedLink).map(_._2)

        user1SharedFeed shouldBe defined
        user2SharedFeed shouldBe defined

        // Both users have the same feed URL but with different read states
        user1SharedFeed.get.link shouldBe sharedFeedLink
        user2SharedFeed.get.link shouldBe sharedFeedLink
        user1SharedFeed.get.userId shouldBe user1.id
        user2SharedFeed.get.userId shouldBe user2.id
        user1SharedFeed.get.isRead shouldBe true // Marked as read for user1
        user2SharedFeed.get.isRead shouldBe false // Still unread for user2
    }

    test("Multiple feeds can be marked as read for a single user") {
        val channel5 = Channel(
            0,
            "User 1 Third Channel",
            "https://example.com/user1/feed3",
            List(
                Feed(
                    link = "https://example.com/user1/feed3/item1",
                    userId = user1.id,
                    channelId = 0,
                    title = "Item 1",
                    description = "Desc",
                    pubDate = Some(OffsetDateTime.now()),
                    isRead = false
                ),
                Feed(
                    link = "https://example.com/user1/feed3/item2",
                    userId = user1.id,
                    channelId = 0,
                    title = "Item 2",
                    description = "Desc",
                    pubDate = Some(OffsetDateTime.now()),
                    isRead = false
                ),
                Feed(
                    link = "https://example.com/user1/feed3/item3",
                    userId = user1.id,
                    channelId = 0,
                    title = "Item 3",
                    description = "Desc",
                    pubDate = Some(OffsetDateTime.now()),
                    isRead = false
                )
            )
        )

        val feedLinks =
            List("https://example.com/user1/feed3/item1", "https://example.com/user1/feed3/item2")

        val result = for {
            _ <- setupUsers(user1)
            _ <- channelRepository.get.insertChannel(channel5, user1)
            markedCount <- feedRepository.get.markFeedAsRead(feedLinks, user1)
            user1Feeds <- channelRepository.get.getChannelsWithFeedsByUser(user1, 50, 0)
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
                    link = "https://example.com/user3/feed/item1",
                    userId = user3.id,
                    channelId = 0,
                    title = "Item 1",
                    description = "Desc",
                    pubDate = Some(OffsetDateTime.now()),
                    isRead = false
                ),
                Feed(
                    link = "https://example.com/user3/feed/item2",
                    userId = user3.id,
                    channelId = 0,
                    title = "Item 2",
                    description = "Desc",
                    pubDate = Some(OffsetDateTime.now()),
                    isRead = false
                )
            )
        )

        val result = for {
            _ <- setupUsers(user3)
            _ <- channelRepository.get.insertChannel(channel6, user3)
            _ <- feedRepository.get.markFeedAsRead(
                List("https://example.com/user3/feed/item1"),
                user3
            )
            // user3 has hideRead = true in settings
            user3Feeds <- channelRepository.get.getChannelsWithFeedsByUser(user3, 50, 0)
        } yield user3Feeds

        val feeds = result.unsafeRunSync()

        // Should only show unread feeds for user3
        feeds should have size 1
        feeds.head._2.link shouldBe "https://example.com/user3/feed/item2"
        feeds.head._2.isRead shouldBe false
    }

    test("User can only delete their own channels") {
        val channel7 = Channel(0, "User 2 Third Channel", "https://example.com/user2/feed3", List())

        val result = for {
            _ <- setupUsers(user1, user2)
            channelId <- channelRepository.get.insertChannel(channel7, user2)
            user1Channels <- channelRepository.get.findUserChannels(user1)
            // Try to delete user2's channel as user1
            deleted <- channelRepository.get.deleteChannel(channelId, user1)
            user2Channels <- channelRepository.get.findUserChannels(user2)
        } yield (channelId, deleted, user2Channels)

        val (cid, deleted, u2Channels) = result.unsafeRunSync()

        deleted shouldBe 0 // user1 cannot delete user2's channel
        u2Channels.exists(_.id == cid) shouldBe true // Channel still exists for user2
    }

    test("User can delete their own channels") {
        val channel8 =
            Channel(0, "User 2 Fourth Channel to Delete", "https://example.com/user2/feed4", List())

        val result = for {
            _ <- setupUsers(user2)
            channelId <- channelRepository.get.insertChannel(channel8, user2)
            channelsBeforeDelete <- channelRepository.get.findUserChannels(user2)
            deleted <- channelRepository.get.deleteChannel(channelId, user2)
            channelsAfterDelete <- channelRepository.get.findUserChannels(user2)
        } yield (channelId, deleted, channelsBeforeDelete, channelsAfterDelete)

        val (cid, deleted, before, after) = result.unsafeRunSync()

        deleted shouldBe 1 // Successfully deleted
        before.exists(_.id == cid) shouldBe true
        after.exists(_.id == cid) shouldBe false
    }

    test("Get unread feeds for a specific user") {
        val channel1 = Channel(
            0,
            "User 1 Channel",
            "https://example.com/user1/feed",
            List(
                Feed(
                    link = "https://example.com/user1/feed/item1",
                    userId = user1.id,
                    channelId = 0,
                    title = "Item 1",
                    description = "Description 1",
                    pubDate = Some(OffsetDateTime.now()),
                    isRead = false
                )
            )
        )

        val channel2 = Channel(
            0,
            "User 2 Channel",
            "https://example.com/user2/feed",
            List(
                Feed(
                    link = "https://example.com/user2/feed/item1",
                    userId = user2.id,
                    channelId = 0,
                    title = "Item 1",
                    description = "Description 1",
                    pubDate = Some(OffsetDateTime.now()),
                    isRead = false
                )
            )
        )

        val result = for {
            _ <- setupUsers(user1, user2)
            _ <- channelRepository.get.insertChannel(channel1, user1)
            _ <- channelRepository.get.insertChannel(channel2, user2)
            user1Unread <- feedRepository.get.getUnreadFeeds(user1)
            user2Unread <- feedRepository.get.getUnreadFeeds(user2)
        } yield (user1Unread, user2Unread)

        val (u1Unread, u2Unread) = result.unsafeRunSync()

        // Both users should have some unread feeds
        u1Unread should not be empty
        u2Unread should not be empty

        // Verify all feeds have the correct userId
        u1Unread.foreach { feed =>
            feed.userId shouldBe user1.id
        }

        u2Unread.foreach { feed =>
            feed.userId shouldBe user2.id
        }
    }
}
