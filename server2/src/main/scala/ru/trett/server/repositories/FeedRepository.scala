package ru.trett.server.repositories

import cats.effect.IO
import doobie.*
import doobie.implicits.*
import doobie.implicits.javatime.*
import doobie.util.transactor.Transactor
import ru.trett.server.models.Feed
import ru.trett.server.models.User

import java.time.OffsetDateTime

class FeedRepository(transactor: Transactor[IO]) {

  def insertFeed(feed: Feed): IO[Int] = {
    sql"""
      INSERT INTO feeds (
        id, channel_id, title, link, description, pub_date, read
      ) VALUES (
        ${feed.id}, ${feed.channelId}, ${feed.title},
        ${feed.link}, ${feed.description}, ${feed.pubDate}, false
      )
    """.update.run.transact(transactor)
  }

  def findFeedById(id: Long): IO[Option[Feed]] = {
    sql"""
      SELECT id, channel_id, title, link, description, pub_date, read 
      FROM feeds 
      WHERE id = $id
    """.query[Feed].option.transact(transactor)
  }

  def deleteFeed(id: Long): IO[Int] = {
    sql"DELETE FROM feeds WHERE id = $id".update.run.transact(transactor)
  }

  def findFeedsByChannelId(channelId: Long): IO[List[Feed]] = {
    sql"""
      SELECT id, channel_id, title, link, description, pub_date, read 
      FROM feeds 
      WHERE channel_id = $channelId
      ORDER BY pub_date DESC
    """.query[Feed].to[List].transact(transactor)
  }

  def markFeedAsRead(ids: List[Long], user: User): IO[Int] = {
    val sql = """
      UPDATE feeds 
      SET read = true 
      WHERE id = ? AND channel_id IN (
        SELECT channel_id 
        FROM user_channels 
        WHERE user_id = ?
      )
    """
    Update[(Long, String)](sql)
      .updateMany(ids.map(id => (id, user.id)))
      .transact(transactor)
  }

  def getUnreadCount(channelId: Long): IO[Int] = {
    sql"""
      SELECT COUNT(*) 
      FROM feeds 
      WHERE channel_id = $channelId AND read = false
    """.query[Int].unique.transact(transactor)
  }

  def updateFeeds(feeds: List[Feed]): IO[Int] = {
    val sql = """
      UPDATE feeds 
      SET title = ?, 
          link = ?, 
          description = ?, 
          pub_date = ?, 
          read = ? 
      WHERE id = ? AND channel_id = ?
    """
    Update[(String, String, String, Option[OffsetDateTime], Boolean, Long, Long)](sql)
      .updateMany(
        feeds.map(f =>
          (
            f.title,
            f.link,
            f.description,
            f.pubDate,
            f.isRead,
            f.id,
            f.channelId
          )
        )
      )
      .transact(transactor)
  }
}
