package ru.trett.rss.server.repositories

import cats.effect.IO
import doobie.*
import doobie.implicits.*
import doobie.postgres.implicits.*
import doobie.util.transactor.Transactor
import ru.trett.rss.server.models.{Feed, User}

import java.time.OffsetDateTime

class FeedRepository(xa: Transactor[IO]):

    given Read[Feed] =
        Read[(String, String, Long, String, String, Option[OffsetDateTime], Boolean)].map {
            case (link, userId, channelId, title, description, pubDate, isRead) =>
                Feed(link, userId, channelId, title, description, pubDate, isRead)
        }

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

    def getTotalUnreadCount(userId: String): IO[Int] =
        sql"""
      SELECT COUNT(*) 
      FROM feeds 
      WHERE user_id = $userId AND read = false
    """.query[Int].unique.transact(xa)

    def getUnreadFeeds(user: User): IO[List[Feed]] =
        sql"""
      SELECT f.link, f.user_id, f.channel_id, f.title, f.description, f.pub_date, f.read
      FROM feeds f
      WHERE f.user_id = ${user.id} AND f.read = false LIMIT 60
    """.query[Feed].to[List].transact(xa)
