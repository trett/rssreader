package ru.trett.server.services

import cats.effect.IO
import ru.trett.server.models.User
import ru.trett.server.repositories.FeedRepository

class FeedService(feedRepository: FeedRepository):

  private val ZoneId = java.time.ZoneId.systemDefault()

  def markAsRead(links: List[String], user: User): IO[Int] =
    feedRepository.markFeedAsRead(links, user)

  def getUnreadCount(channelId: Long): IO[Int] =
    feedRepository.getUnreadCount(channelId)

