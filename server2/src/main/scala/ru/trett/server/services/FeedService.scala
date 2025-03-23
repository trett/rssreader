package ru.trett.server.services

import cats.effect.IO
import ru.trett.server.models.Feed
import ru.trett.server.models.User
import ru.trett.server.repositories.FeedRepository

import java.time.Instant
import java.time.OffsetDateTime

class FeedService(feedRepository: FeedRepository) {

  private val ZoneId = java.time.ZoneId.systemDefault()

  def createFeed(channelId: Long, title: String, link: String, description: String): IO[Int] = {
    val feed = Feed(
      id = 0L, 
      channelId = channelId,
      title = title, 
      link = link, 
      description = description,
      pubDate = Some(OffsetDateTime.ofInstant(Instant.now(), ZoneId)),
      isRead = false
    )
    feedRepository.insertFeed(feed)
  }

  def getFeedById(id: Long): IO[Option[Feed]] = {
    feedRepository.findFeedById(id)
  }

  def getFeedsByChannel(channelId: Long): IO[List[Feed]] = {
    feedRepository.findFeedsByChannelId(channelId)
  }

  def markAsRead(ids: List[Long], user: User): IO[Int] = {
    feedRepository.markFeedAsRead(ids, user)
  }

  def getUnreadCount(channelId: Long): IO[Int] = {
    feedRepository.getUnreadCount(channelId)
  }

  def removeFeed(id: Long): IO[Int] = {
    feedRepository.deleteFeed(id)
  }
}

