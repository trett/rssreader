package ru.trett.rss.server.repositories

import cats.effect.IO
import cats.syntax.all.*
import doobie.*
import doobie.implicits.*
import doobie.postgres.implicits.*
import doobie.util.transactor.Transactor
import ru.trett.rss.server.models.{Channel, Feed, User}

import java.time.OffsetDateTime
import FeedInstances.given

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
            INSERT INTO feeds (link, user_id, channel_id, title, description, pub_date, read, image_url, categories, important)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (link, user_id)
            DO UPDATE SET description = EXCLUDED.description,
            pub_date = EXCLUDED.pub_date, title = EXCLUDED.title, channel_id = EXCLUDED.channel_id,
            image_url = EXCLUDED.image_url, categories = EXCLUDED.categories
            """
        type FeedInfo =
            (
                String,
                String,
                Long,
                String,
                String,
                Option[OffsetDateTime],
                Boolean,
                Option[String],
                List[String],
                Boolean
            )
        Update[FeedInfo](sql)
            .updateMany(
                feeds.map(f =>
                    (
                        f.link,
                        userId,
                        channelId,
                        f.title,
                        f.description,
                        f.pubDate,
                        f.isRead,
                        f.imageUrl,
                        f.categories,
                        f.important
                    )
                )
            )

    def findUserChannelsWithHighlight(user: User): IO[List[(Channel, Boolean)]] =
        sql"""
          SELECT c.id, c.title, c.link, uc.highlighted
          FROM channels c
          JOIN user_channels uc ON c.id = uc.channel_id
          WHERE uc.user_id = ${user.id}
         """
            .query[(Channel, Boolean)]
            .to[List]
            .transact(xa)

    def getChannelsWithFeedsByUser(
        user: User,
        limit: Int,
        offset: Int,
        importantOnly: Boolean = false
    ): IO[List[(Channel, Feed, Boolean)]] =
        val query = fr"""
          SELECT c.id, c.title, c.link,
          f.link, f.user_id, f.channel_id, f.title, f.description, f.pub_date, f.read, f.image_url, f.categories, f.important,
          uc.highlighted
          FROM channels c
          JOIN user_channels uc ON c.id = uc.channel_id
          JOIN feeds f ON c.id = f.channel_id AND f.user_id = ${user.id}
          WHERE uc.user_id = ${user.id}
        """
        val hideReadFilter =
            if user.settings.hideRead then fr"AND f.read = false" else fr""
        val importantFilter =
            if importantOnly then fr"AND (f.important = true OR uc.highlighted = true)" else fr""
        // Banned categories do not apply to explicitly highlighted channels
        val bannedFilter =
            if importantOnly && user.settings.bannedCategories.nonEmpty then
                fr"AND (uc.highlighted = true OR NOT (f.categories && ${user.settings.bannedCategories}::text[]))"
            else fr""
        (query ++ hideReadFilter ++ importantFilter ++ bannedFilter ++
            fr"ORDER BY f.pub_date DESC LIMIT $limit OFFSET $offset")
            .query[(Channel, Feed, Boolean)]
            .to[List]
            .transact(xa)

    def getExistingFeedLinksByChannels(
        channelIds: List[Long],
        userId: String
    ): IO[Map[Long, Set[String]]] =
        channelIds match
            case Nil => IO.pure(Map.empty)
            case ids =>
                (fr"SELECT channel_id, link FROM feeds WHERE" ++
                    Fragments.in(fr"channel_id", cats.data.NonEmptyList.fromListUnsafe(ids)) ++
                    fr"AND user_id = $userId")
                    .query[(Long, String)]
                    .to[List]
                    .transact(xa)
                    .map(_.groupBy(_._1).view.mapValues(_.map(_._2).toSet).toMap)

    def insertFeeds(feeds: List[Feed], channelId: Long, userId: String): IO[Int] =
        insertFeedItemsQuery(channelId, userId, feeds)
            .transact(xa)

    def deleteChannel(id: Long, user: User): IO[Int] =
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

    def updateChannelHighlight(id: Long, user: User, highlighted: Boolean): IO[Int] =
        sql"""
          UPDATE user_channels
          SET highlighted = $highlighted
          WHERE user_id = ${user.id} AND channel_id = $id
        """.update.run.transact(xa)
