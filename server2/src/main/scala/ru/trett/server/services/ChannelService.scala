package ru.trett.server.services

import cats.effect.IO
import cats.implicits._
import com.rometools.rome.io.SyndFeedInput
import com.rometools.rome.io.XmlReader
import org.http4s.Method
import org.http4s.Request
import org.http4s.Uri
import org.http4s.ember.client.EmberClientBuilder
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.SelfAwareStructuredLogger
import ru.trett.server.models.Channel
import ru.trett.server.models.Feed
import ru.trett.server.models.User
import ru.trett.server.repositories.ChannelRepository

import java.time.Instant
import scala.jdk.CollectionConverters.*
import ru.trett.server.repositories.FeedRepository

class ChannelService(
    channelRepository: ChannelRepository,
    feedsRepository: FeedRepository
)(using
    LoggerFactory[IO]
) {

  private val ZoneId = java.time.ZoneId.systemDefault()

  private val logger: SelfAwareStructuredLogger[IO] =
    LoggerFactory[IO].getLogger

  def createChannel(link: String, user: User): IO[Unit] = {
    for {
      channel <- getChannel(link)
      result <- channelRepository.insertChannel(channel, user)
    } yield result
  }

  def updateFeeds(user: User): IO[List[IO[Int]]] = {
    for {
      _ <- logger.info("Starting to update feeds")
      channels <- channelRepository.findChannelsByUser(user)
      feeds <- channels.traverse { channel =>
        logger.info(s"Updating channel: ${channel.title}")
        getChannel(channel.channelLink)
          .map(channel => feedsRepository.updateFeeds(channel.feedItems))
      }
    } yield feeds
  }

  def getChannelById(id: Long): IO[Option[Channel]] =
    channelRepository.findChannelById(id)

  def getAllChannels(user: User): IO[List[Channel]] =
    channelRepository.findChannelsByUser(user)

  def removeChannel(id: Long, user: User): IO[Int] =
    channelRepository.deleteChannel(id, user)

  private def getChannel(link: String): IO[Channel] = {
    val client = EmberClientBuilder.default[IO].build
    for {
      uri <- IO.fromEither(Uri.fromString(link))
      request = Request[IO](Method.GET, uri)
      channel <- client.use { client =>
        client
          .get(link) { response =>
            response.body.compile.to(Array).flatMap { bytes =>
              val stream = new java.io.ByteArrayInputStream(bytes)
              parse(stream)
            }
          }
      }
    } yield channel
  }

  private def parse(stream: java.io.InputStream): IO[Channel] = {
    for {
      _ <- logger.info("Starting to parse RSS feed")
      input = new SyndFeedInput()
      xmlReader = new XmlReader(stream)
      syndFeed <- IO.blocking(input.build(xmlReader))
      title = syndFeed.getTitle
      _ <- logger.info(
        s"Parsing the channel: title '$title', type '${syndFeed.getFeedType}'"
      )

      feedItems = syndFeed.getEntries.asScala.map { entry =>
        Feed(
          id = 0L,
          channelId = 0L, // This will be set after channel creation
          title = entry.getTitle,
          link = entry.getLink,
          description = extractDescription(entry),
          pubDate = Some(extractDate(entry)),
          isRead = false
        )
      }.toList

      channel = Channel(
        id = 0L,
        channelLink = syndFeed.getLink,
        title = title,
        link = syndFeed.getLink,
        feedItems = feedItems
      )

      _ <- logger.info(s"Parsed ${feedItems.size} items")
      _ <- logger.info("End of parsing the channel")
    } yield channel
  }

  private def extractDescription(
      entry: com.rometools.rome.feed.synd.SyndEntry
  ): String = {
    Option(entry.getDescription)
      .orElse {
        Option(entry.getContents)
          .filter(_.size > 0)
          .map(_.get(0))
      }
      .map(_.getValue)
      .getOrElse("")
  }

  private def extractDate(
      entry: com.rometools.rome.feed.synd.SyndEntry
  ): Instant = {
    Option(entry.getPublishedDate)
      .orElse(Option(entry.getUpdatedDate))
      .map(_.toInstant)
      .getOrElse(
        throw new RuntimeException(
          s"Date must not be empty! Feed: ${entry.getUri}"
        )
      )
  }
}
