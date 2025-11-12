package ru.trett.rss.server.repositories

import cats.effect.IO
import cats.syntax.all.*
import doobie.*
import doobie.implicits.*
import doobie.postgres.implicits.*
import doobie.util.transactor.Transactor
import ru.trett.rss.server.models.{Channel, Feed, User}

import java.time.OffsetDateTime

class ChannelRepository(xa: Transactor[IO]):

    given Read[Channel] = Read[(Long, String, String)].map { case (id, title, link) =>
        Channel(id, title, link)
    }

    def insertChannel(channel: Channel, user: User): IO[Long] =
        val insertChannelQuery =
            sql"""
              INSERT INTO channels (title, link) 
              VALUES (${channel.title}, ${channel.link})
              """.update.withUniqueGeneratedKeys[Long]("id")

        def insertUserChannelsQuery(userId: String, channelId: Long) =
            sql"""
                INSERT INTO user_channels (user_id, channel_id)
                VALUES ($userId, $channelId)
            """.update.run

        val transaction = for {
            channelId <- insertChannelQuery
            _ <- insertUserChannelsQuery(user.id, channelId)
            _ <- insertFeedItemsQuery(channelId, user.id, channel.feedItems)
        } yield channelId
        transaction.transact(xa)

    private def insertFeedItemsQuery(
        channelId: Long,
        userId: String,
        feeds: List[Feed]
    ): ConnectionIO[Int] =
        val sql =
            """
            INSERT INTO feeds (link, user_id, channel_id, title, description, pub_date, read)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (link, user_id)
            DO UPDATE SET description = EXCLUDED.description,
            pub_date = EXCLUDED.pub_date, title = EXCLUDED.title, channel_id = EXCLUDED.channel_id
            """
        type FeedInfo =
            (String, String, Long, String, String, Option[OffsetDateTime], Boolean)
        Update[FeedInfo](sql)
            .updateMany(
                feeds.map(f =>
                    (f.link, userId, channelId, f.title, f.description, f.pubDate, f.isRead)
                )
            )

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

    def getChannelsWithFeedsByUser(
        user: User,
        limit: Int,
        offset: Int
    ): IO[List[(Channel, Feed)]] = {
        val query = fr"""
          SELECT c.id, c.title, c.link,
          f.link, f.user_id, f.channel_id, f.title, f.description, f.pub_date, f.read
          FROM channels c
          JOIN user_channels uc ON c.id = uc.channel_id
          JOIN feeds f ON c.id = f.channel_id AND f.user_id = ${user.id}
          WHERE uc.user_id = ${user.id}
        """
        val condition =
            if (user.settings.hideRead)
                fr"AND f.read = false ORDER BY f.pub_date DESC LIMIT $limit OFFSET $offset"
            else
                fr"ORDER BY f.pub_date DESC LIMIT $limit OFFSET $offset"
        val finalQuery = query ++ condition
        finalQuery
            .query[(Channel, Feed)]
            .to[List]
            .transact(xa)
    }

    def insertFeeds(feeds: List[Feed], channelId: Long, userId: String): IO[Int] =
        insertFeedItemsQuery(channelId, userId, feeds)
            .transact(xa)

    def deleteChannel(id: Long, user: User): IO[Int] = {
        val checkPermissionsQuery = sql"""
              SELECT COUNT(1)
              FROM user_channels
              WHERE user_id = ${user.id} AND channel_id = $id
            """
            .query[Int]
            .unique
        val deleteQuery = sql"DELETE FROM channels WHERE id = $id".update.run
        val transaction = for {
            count <- checkPermissionsQuery
            deleted <- count match {
                case 0 => 0.pure[ConnectionIO]
                case _ => deleteQuery
            }
        } yield deleted
        transaction.transact(xa)
    }
