package ru.trett.rss.server.services

import cats.effect.IO
import ru.trett.rss.models.{ChannelNews, FeedItemData}
import ru.trett.rss.server.models.{Feed, User}
import ru.trett.rss.server.repositories.FeedRepository

import java.time.OffsetDateTime

class FeedService(feedRepository: FeedRepository):

    def markAsRead(links: List[String], user: User): IO[Int] =
        feedRepository.markFeedAsRead(links, user)

    def getUnreadCount(channelId: Long, userId: String): IO[Int] =
        feedRepository.getUnreadCount(channelId, userId)

    def getTotalUnreadCount(userId: String, importantOnly: Boolean = false): IO[Int] =
        feedRepository.getTotalUnreadCount(userId, importantOnly)

    def getTotalCount(userId: String, importantOnly: Boolean = false): IO[Int] =
        feedRepository.getTotalCount(userId, importantOnly)

    /** Every channel's unread count in one call, for the reader's sidebar. */
    def getUnreadCountByChannel(
        userId: String,
        importantOnly: Boolean = false
    ): IO[Map[Long, Int]] =
        feedRepository.getUnreadCountByChannel(userId, importantOnly)

    /** All channels' important news in the range, grouped by channel (newest first within each),
      * capped to `limitPerChannel` items per channel. Channels are ordered by their newest item.
      */
    def getNewsByDateGrouped(
        user: User,
        from: OffsetDateTime,
        to: OffsetDateTime,
        limitPerChannel: Int
    ): IO[List[ChannelNews]] =
        feedRepository.getFeedsByDateRangeAllChannels(user, from, to, limitPerChannel).map { rows =>
            rows
                .groupBy { case (feed, title) => (feed.channelId, title) }
                .map { case ((channelId, title), grouped) =>
                    ChannelNews(channelId, title, grouped.map(toItem))
                }
                .toList
                .sortBy(_.items.headOption.map(_.pubDate))(Ordering[Option[OffsetDateTime]].reverse)
        }

    private def toItem(row: (Feed, String)): FeedItemData =
        val (feed, channelTitle) = row
        FeedItemData(
            feed.link,
            channelTitle,
            feed.title,
            feed.description,
            feed.pubDate.getOrElse(OffsetDateTime.now()),
            feed.isRead,
            imageUrl = feed.imageUrl,
            important = feed.important
        )
