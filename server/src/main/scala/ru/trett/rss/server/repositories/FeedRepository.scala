package ru.trett.rss.server.repositories

import cats.effect.IO
import doobie.*
import doobie.implicits.*
import doobie.postgres.implicits.*
import doobie.util.transactor.Transactor
import ru.trett.rss.server.models.{Feed, User}

import java.time.OffsetDateTime
import FeedInstances.given

class FeedRepository(xa: Transactor[IO]):

    def markFeedAsRead(links: List[String], user: User): IO[Int] =
        val sql = """
      UPDATE feeds
      SET read = true
      WHERE link = ? AND user_id = ?
    """
        Update[(String, String)](sql)
            .updateMany(links.map(link => (link, user.id)))
            .transact(xa)

    def getUnreadCount(channelId: Long, userId: String): IO[Int] =
        sql"""
      SELECT COUNT(*)
      FROM feeds
      WHERE channel_id = $channelId AND user_id = $userId AND read = false
    """.query[Int].unique.transact(xa)

    def getTotalUnreadCount(userId: String, importantOnly: Boolean = false): IO[Int] =
        val importantFilter = if importantOnly then fr"AND important = true" else fr""
        (fr"SELECT COUNT(*) FROM feeds WHERE user_id = $userId AND read = false" ++ importantFilter)
            .query[Int]
            .unique
            .transact(xa)

    def getUnreadFeeds(user: User, limit: Int): IO[List[Feed]] =
        getUnreadFeeds(user, limit, 0)

    def getUnreadFeeds(user: User, limit: Int, offset: Int): IO[List[Feed]] =
        sql"""
      SELECT f.link, f.user_id, f.channel_id, f.title, f.description, f.pub_date, f.read, f.image_url, f.categories, f.important
      FROM feeds f
      WHERE f.user_id = ${user.id} AND f.read = false
      ORDER BY f.pub_date DESC
      LIMIT $limit OFFSET $offset
    """.query[Feed].to[List].transact(xa)

    def getFeedsByDateRange(
        user: User,
        from: OffsetDateTime,
        to: OffsetDateTime,
        limit: Int,
        importantOnly: Boolean = false
    ): IO[List[(Feed, String)]] =
        val importantFilter = if importantOnly then fr"AND f.important = true" else fr""
        (fr"""
      SELECT f.link, f.user_id, f.channel_id, f.title, f.description, f.pub_date, f.read, f.image_url, f.categories, f.important,
             c.title
      FROM feeds f
      JOIN channels c ON c.id = f.channel_id
      WHERE f.user_id = ${user.id} AND f.pub_date >= $from AND f.pub_date <= $to
    """ ++ importantFilter ++ fr"ORDER BY f.pub_date DESC LIMIT $limit")
            .query[(Feed, String)]
            .to[List]
            .transact(xa)

    def updateFeedImportance(feeds: List[Feed]): IO[Int] =
        if feeds.isEmpty then IO.pure(0)
        else
            // Use (? OR read) so that a user's manual mark-as-read is never overwritten:
            // - isRead=true (not important → auto-read): (true OR read) = true  ✓
            // - isRead=false (important → keep unread): (false OR read) = read  ✓
            Update[(Boolean, Boolean, String, String)](
                "UPDATE feeds SET important = ?, read = (? OR read) WHERE link = ? AND user_id = ?"
            ).updateMany(feeds.map(f => (f.important, f.isRead, f.link, f.userId)))
                .transact(xa)
