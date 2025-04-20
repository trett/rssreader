package ru.trett.rss.server.repositories

import cats.effect.IO
import doobie.*
import doobie.implicits.*
import doobie.postgres.implicits.*
//import doobie.implicits.javatime.*
import doobie.util.transactor.Transactor
import ru.trett.rss.server.models.{Feed, User}

import java.time.OffsetDateTime

class FeedRepository(xa: Transactor[IO]):

    given Read[Feed] = Read[(String, Long, String, String, Option[OffsetDateTime], Boolean)].map {
        case (link, channelId, title, description, pubDate, isRead) =>
            Feed(link, channelId, title, description, pubDate, isRead)
    }

//    def findFeedsByChannelId(channelId: Long): IO[List[Feed]] =
//        sql"""
//      SELECT link, channel_id, title, description, pub_date, read
//      FROM feeds
//      WHERE channel_id = $channelId
//      ORDER BY pub_date DESC
//    """.query[Feed].to[List].transact(xa)

    def markFeedAsRead(links: List[String], user: User): IO[Int] =
        val sql = """
      UPDATE feeds
      SET read = true
      WHERE link = ? AND channel_id IN (
        SELECT channel_id
        FROM user_channels
        WHERE user_id = ?
      )
    """
        Update[(String, String)](sql)
            .updateMany(links.map(link => (link, user.id)))
            .transact(xa)

    def getUnreadCount(channelId: Long): IO[Int] =
        sql"""
      SELECT COUNT(*) 
      FROM feeds 
      WHERE channel_id = $channelId AND read = false
    """.query[Int].unique.transact(xa)
