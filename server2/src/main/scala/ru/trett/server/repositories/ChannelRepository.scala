package ru.trett.server.repositories

import cats.effect.IO
import doobie.*
import doobie.implicits.*
import doobie.postgres.implicits.*
import doobie.util.transactor.Transactor
import ru.trett.server.models.{Channel, Feed, User}
import java.time.OffsetDateTime

class ChannelRepository(xa: Transactor[IO]):

  given Read[Channel] = Read[(Long, String, String)].map {
    case (id, title, link) => Channel(id, title, link)
  }

  def insertChannel(channel: Channel, user: User): IO[Long] =
    val insertChannelQuery = sql"""
      INSERT INTO channels (title, link) 
      VALUES (${channel.title}, ${channel.link})
    """.update.withUniqueGeneratedKeys[Long]("id")

    def insertUserChannelsQuery(userId: String, channelId: Long) =
      sql"""
        INSERT INTO user_channels (user_id, channel_id)
        VALUES (${userId}, ${channelId})
      """.update.run

    val transaction = for {
      channelId <- insertChannelQuery
      _ <- insertUserChannelsQuery(user.id, channelId)
      _ <- insertFeedItemsQuery(channelId, channel.feedItems)
    } yield channelId
    transaction.transact(xa)

  def findUserChannels(user: User): IO[List[Channel]] =
    sql"""
     SELECT c.id, c.title, c.link
      FROM channels c
      JOIN user_channels uc ON c.id = uc.channel_id
      WHERE uc.user_id = ${user.id}
    """
      .query[Channel]
      .to[List]
      .transact(xa)

  def getChannelsWithFeedsByUser(user: User): IO[List[Channel]] =
    sql"""
      SELECT c.id, c.title, c.link, 
      f.link, f.channel_id, f.title, f.description, f.pub_date, f.read
      FROM channels c
      JOIN user_channels uc ON c.id = uc.channel_id
      JOIN feeds f ON c.id = f.channel_id
      WHERE uc.user_id = ${user.id}
    """
      .query[(Channel, Feed)]
      .to[List]
      .map(
        _.groupBy(_._1)
          .map((channel, feeds) => channel.copy(feedItems = feeds.map(_._2)))
          .toList
      )
      .transact(xa)

  def insertFeeds(feeds: List[Feed], channelId: Long): IO[Int] =
    insertFeedItemsQuery(channelId, feeds)
      .transact(xa)

  def deleteChannel(id: Long, user: User): IO[Int] =
    val checkChannelQuery = sql"""
      SELECT COUNT(*) 
      FROM user_channels 
      WHERE user_id = ${user.id} AND channel_id = $id
    """.query[Int].unique

    checkChannelQuery
      .transact(xa)
      .flatMap:
        case 0 =>
          IO.raiseError(new Exception("Channel does not belong to the user"))
        case _ =>
          sql"DELETE FROM channels WHERE id = $id".update.run
            .transact(xa)

  private def insertFeedItemsQuery(
      channelId: Long,
      feeds: List[Feed]
  ): ConnectionIO[Int] =
    val sql = """
        INSERT INTO feeds (channel_id, title, link, description, pub_date, read)
        VALUES (?, ?, ?, ?, ?, ?) 
        ON CONFLICT (link) 
        DO UPDATE SET description = EXCLUDED.description, 
        pub_date = EXCLUDED.pub_date, read = EXCLUDED.read
      """
    type FeedInfo =
      (Long, String, String, String, Option[OffsetDateTime], Boolean)
    Update[FeedInfo](sql)
      .updateMany(
        feeds.map(f =>
          (channelId, f.title, f.link, f.description, f.pubDate, f.isRead)
        )
      )
