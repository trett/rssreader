package ru.trett.rss.server.services

import cats.effect.IO
import ru.trett.rss.server.models.User
import ru.trett.rss.server.repositories.FeedRepository

class FeedService(feedRepository: FeedRepository):

    def markAsRead(links: List[String], user: User): IO[Int] =
        feedRepository.markFeedAsRead(links, user)

    def getUnreadCount(channelId: Long): IO[Int] =
        feedRepository.getUnreadCount(channelId)
