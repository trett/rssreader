package ru.trett.rss.server.services

import cats.effect.IO
import ru.trett.rss.models.FeedItemData
import ru.trett.rss.server.models.User
import ru.trett.rss.server.repositories.FeedRepository

import java.time.OffsetDateTime

class FeedService(feedRepository: FeedRepository):

    def markAsRead(links: List[String], user: User): IO[Int] =
        feedRepository.markFeedAsRead(links, user)

    def getUnreadCount(channelId: Long, userId: String): IO[Int] =
        feedRepository.getUnreadCount(channelId, userId)

    def getTotalUnreadCount(userId: String, importantOnly: Boolean = false): IO[Int] =
        feedRepository.getTotalUnreadCount(userId, importantOnly)

    def getFeedsByDateRange(
        user: User,
        from: OffsetDateTime,
        to: OffsetDateTime,
        limit: Int,
        importantOnly: Boolean = false
    ): IO[List[FeedItemData]] =
        feedRepository.getFeedsByDateRange(user, from, to, limit, importantOnly).map {
            _.map { case (feed, channelTitle) =>
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
            }
        }
