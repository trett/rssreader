package ru.trett.server.repositories

import cats.effect.IO
import doobie.*
import doobie.implicits.*
import doobie.implicits.javatime.*
import doobie.util.transactor.Transactor
import ru.trett.server.models.Channel
import ru.trett.server.models.User
import ru.trett.server.models.Feed
import java.time.Instant

class ChannelRepository(transactor: Transactor[IO]) {

  def insertChannel(channel: Channel, user: User): IO[Unit] = {
    val insertChannelQuery = sql"""
      INSERT INTO channels (channel_link, title, link) 
      VALUES (${channel.channelLink}, ${channel.title}, ${channel.link})
    """

    def insertUserChannelsQuery(userId: String, channelId: Long): Fragment =
      sql"""
        INSERT INTO user_channels (user_id, channel_id)
        VALUES (${userId}, ${channelId})
      """

    def insertFeedItemsQuery(
        channelId: Long,
        feeds: List[Feed]
    ): ConnectionIO[Int] =
      val sql = """
        UPDATE feeds 
        SET channel_id = ?, title = ?, link = ?, description = ?, pub_date = ?, read = ?
        """
      type FeedInfo = (Long, String, String, String, Option[Instant], Boolean)
      Update[FeedInfo](
        sql
      ).updateMany {
        feeds.map { f =>
          (f.channelId, f.title, f.link, f.description, f.pubDate, f.isRead)
        }
      }

    val insertChannelTransaction = for {
      channelId <- insertChannelQuery.update.withGeneratedKeys[Long]("id")
      _ <- fs2.Stream(insertUserChannelsQuery(user.id, channelId).update.run)
      _ <- fs2.Stream(insertFeedItemsQuery(channelId, channel.feedItems))
    } yield channelId

    insertChannelTransaction.compile.drain.transact(transactor)
  } 

  def findChannelById(id: Long): IO[Option[Channel]] = {
    sql"""
      SELECT id, channel_link, title, link 
      FROM channels 
      WHERE id = $id
    """
      .query[Channel]
      .option
      .transact(transactor)
  }

  def findChannelsByUser(user: User): IO[List[Channel]] = {
    sql"""
      SELECT c.id, c.channel_link, c.title, c.link 
      FROM channels c
      JOIN user_channels uc ON c.id = uc.channel_id
      JOIN feeds f ON c.id = f.channel_id
      WHERE uc.user_id = ${user.id}
    """
      .query[Channel]
      .to[List]
      .transact(transactor)
  }

  def deleteChannel(id: Long, user: User): IO[Int] = {
    // check if the channel belongs to the user
    val checkChannelQuery = sql"""
      SELECT COUNT(*) 
      FROM user_channels 
      WHERE user_id = ${user.id} AND channel_id = $id
    """.query[Int].unique
    checkChannelQuery.transact(transactor).flatMap { count =>
      if (count == 0) {
        IO.raiseError(new Exception("Channel does not belong to the user"))
      } else {
        sql"DELETE FROM channels WHERE id = $id".update.run
          .transact(transactor)
      }
    }
  }
}
