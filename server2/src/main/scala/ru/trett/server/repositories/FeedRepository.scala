package ru.trett.server.repositories

import cats.effect.IO
import doobie.*
import doobie.implicits.*
import doobie.implicits.javatime.*
import doobie.util.transactor.Transactor
import ru.trett.server.models.Feed

import java.time.Instant

class FeedRepository(transactor: Transactor[IO]) {

  def insertFeed(feed: Feed): IO[Int] = {
    sql"""
      INSERT INTO feeds (
        id, channelId, title, link, description, pubDate, isRead
      ) VALUES (
        ${feed.id}, ${feed.channelId}, ${feed.title}
        ${feed.link}, ${feed.description}, ${feed.pubDate}, false
      )
    """.update.run.transact(transactor)
  }

  def findFeedById(id: Long): IO[Option[Feed]] = {
    sql"""
      SELECT id, channelId, title, link, description, pubDate, isRead 
      FROM feeds 
      WHERE id = $id
    """.query[Feed].option.transact(transactor)
  }

  def deleteFeed(id: Long): IO[Int] = {
    sql"DELETE FROM feeds WHERE id = $id"
      .update.run.transact(transactor)
  }

  def findFeedsByChannelId(channelId: Long): IO[List[Feed]] = {
    sql"""
      SELECT id, channelId, title, link, description, pubDate, isRead 
      FROM feeds 
      WHERE channelId = $channelId
      ORDER BY pubDate DESC
    """.query[Feed].to[List].transact(transactor)
  }

  def markFeedAsRead(id: Long): IO[Int] = {
    sql"UPDATE feeds SET isRead = true WHERE id = $id"
      .update.run.transact(transactor)
  }

  def getUnreadCount(channelId: Long): IO[Int] = {
    sql"""
      SELECT COUNT(*) 
      FROM feeds 
      WHERE channelId = $channelId AND isRead = false
    """.query[Int].unique.transact(transactor)
  }

  def updateFeeds(feeds: List[Feed]): IO[Int] = {
    val sql = """
      UPDATE feeds 
      SET title = ?, 
          link = ?, 
          description = ?, 
          pubDate = ?, 
          isRead = ? 
      WHERE id = ? AND channelId = ?
    """
    
    Update[(String, String, String, Option[Instant], Boolean, Long, Long)](sql)
      .updateMany(
        feeds.map(f => 
          (f.title, f.link, f.description, f.pubDate, f.isRead, f.id, f.channelId)
        )
      )
      .transact(transactor)
  }
}

