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

    private def bannedCategoriesFilter(user: User): Fragment =
        if user.settings.bannedCategories.nonEmpty then
            fr"AND (uc.highlighted = true OR NOT (f.categories && ${user.settings.bannedCategories}::text[]))"
        else fr""

    def getUnreadCount(channelId: Long, user: User, importantOnly: Boolean = false): IO[Int] =
        if !importantOnly then sql"""
              SELECT COUNT(*)
              FROM feeds
              WHERE channel_id = $channelId AND user_id = ${user.id} AND read = false
            """.query[Int].unique.transact(xa)
        else
            (fr"""
              SELECT COUNT(*)
              FROM feeds f
              JOIN user_channels uc ON f.channel_id = uc.channel_id AND uc.user_id = ${user.id}
              WHERE f.channel_id = $channelId AND f.user_id = ${user.id} AND f.read = false
                AND (f.important = true OR uc.highlighted = true)
            """ ++ bannedCategoriesFilter(user))
                .query[Int]
                .unique
                .transact(xa)

    def getUnreadCount(channelId: Long, userId: String, importantOnly: Boolean): IO[Int] =
        getUnreadCount(channelId, User(userId, "", "", User.Settings()), importantOnly)

    def getTotalUnreadCount(user: User, importantOnly: Boolean = false): IO[Int] =
        if !importantOnly then sql"""
              SELECT COUNT(*)
              FROM feeds
              WHERE user_id = ${user.id} AND read = false
            """.query[Int].unique.transact(xa)
        else
            (fr"""
              SELECT COUNT(*)
              FROM feeds f
              JOIN user_channels uc ON f.channel_id = uc.channel_id AND uc.user_id = ${user.id}
              WHERE f.user_id = ${user.id} AND f.read = false
                AND (f.important = true OR uc.highlighted = true)
            """ ++ bannedCategoriesFilter(user))
                .query[Int]
                .unique
                .transact(xa)

    def getTotalUnreadCount(userId: String, importantOnly: Boolean): IO[Int] =
        getTotalUnreadCount(User(userId, "", "", User.Settings()), importantOnly)

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

    /** Important-or-highlighted feeds across all of the user's channels within the date range,
      * capped to `limitPerChannel` items per channel (newest first) so a busy feed can't dominate.
      * Returned ordered by channel, then newest first, ready to group by channel.
      */
    def getFeedsByDateRangeAllChannels(
        user: User,
        from: OffsetDateTime,
        to: OffsetDateTime,
        limitPerChannel: Int
    ): IO[List[(Feed, String)]] =
        sql"""
      SELECT link, user_id, channel_id, title, description, pub_date, read, image_url, categories, important,
             channel_title
      FROM (
        SELECT f.link, f.user_id, f.channel_id, f.title, f.description, f.pub_date, f.read, f.image_url, f.categories, f.important,
               c.title AS channel_title,
               ROW_NUMBER() OVER (PARTITION BY f.channel_id ORDER BY f.pub_date DESC) AS rn
        FROM feeds f
        JOIN channels c ON c.id = f.channel_id
        JOIN user_channels uc ON uc.channel_id = f.channel_id AND uc.user_id = ${user.id}
        WHERE f.user_id = ${user.id}
          AND f.pub_date >= $from AND f.pub_date <= $to
          AND (f.important = true OR uc.highlighted = true)
      ) ranked
      WHERE rn <= $limitPerChannel
      ORDER BY channel_id, pub_date DESC
    """
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
