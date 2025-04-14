package ru.trett.rss.server.services

import cats.effect.IO
import cats.syntax.all.*
import com.rometools.rome.feed.synd.SyndEntry
import com.rometools.rome.io.SyndFeedInput
import com.rometools.rome.io.XmlReader
import org.http4s.Method
import org.http4s.Request
import org.http4s.Uri
import org.http4s.ember.client.EmberClientBuilder
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.SelfAwareStructuredLogger
import ru.trett.rss.server.models.Channel
import ru.trett.rss.server.models.Feed
import ru.trett.rss.server.models.User
import ru.trett.rss.server.repositories.ChannelRepository

import java.time.OffsetDateTime
import scala.jdk.CollectionConverters.*
import ru.trett.rss.models.FeedItemData

class ChannelService(channelRepository: ChannelRepository)(using LoggerFactory[IO]):

    private val ZoneId = java.time.ZoneId.systemDefault()

    private val logger: SelfAwareStructuredLogger[IO] =
        LoggerFactory[IO].getLogger

    def createChannel(link: String, user: User): IO[Long] =
        for {
            channel <- getChannel(link)
            result <- channelRepository.insertChannel(channel, user)
        } yield result

    def updateFeeds(user: User): IO[List[Int]] =
        for {
            channels <- channelRepository.findUserChannels(user)
            result <- channels.traverse { channel =>
                for {
                    _ <- logger.info(s"Updating channel: ${channel.title}")
                    updatedChannel <- getChannel(channel.link)
                    rows <- channelRepository.insertFeeds(updatedChannel.feedItems, channel.id)
                    _ <- logger.info(s"Inserted ${rows} feeds for channel: ${channel.title}")
                } yield rows
            }
        } yield result

    def getAllChannels(user: User): IO[List[FeedItemData]] =
        val channels = channelRepository.getChannelsWithFeedsByUser(user)
        channels.flatMap {
            _.traverse { case (channel, feed) =>
                IO.pure(
                    FeedItemData(
                        feed.link,
                        channel.title,
                        feed.title,
                        feed.description,
                        feed.pubDate.getOrElse(OffsetDateTime.now()),
                        feed.isRead
                    )
                )
            }
        }

    def removeChannel(id: Long, user: User): IO[Int] =
        channelRepository.deleteChannel(id, user)

    private def getChannel(link: String): IO[Channel] =
        val client = EmberClientBuilder.default[IO].build
        for {
            uri <- IO.fromEither(Uri.fromString(link))
            request = Request[IO](Method.GET, uri)
            channel <- client.use { client =>
                client
                    .get(link) { response =>
                        response.body.compile.to(Array).flatMap { bytes =>
                            val stream = new java.io.ByteArrayInputStream(bytes)
                            parse(stream, link)
                        }
                    }
            }
        } yield channel

    private def parse(stream: java.io.InputStream, link: String): IO[Channel] = {
        for {
            _ <- logger.info(s"Starting to parse RSS feed: $link")
            input = new SyndFeedInput()
            xmlReader = new XmlReader(stream)
            syndFeed <- IO.blocking(input.build(xmlReader))
            title = syndFeed.getTitle
            _ <- logger.info(s"Parsing the channel: title '$title', type '${syndFeed.getFeedType}'")

            feedItems = syndFeed.getEntries.asScala.map { entry =>
                Feed(
                    channelId = 0L, // This will be set after channel creation
                    title = entry.getTitle,
                    link = entry.getLink(),
                    description = extractDescription(entry),
                    pubDate = extractDate(entry),
                    isRead = false
                )
            }.toList

            channel = Channel(id = 0L, title = title, link = link, feedItems = feedItems)

            _ <- logger.info(s"Parsed ${feedItems.size} items")
            _ <- logger.info("End of parsing the channel")
        } yield channel
    }

    private def extractDescription(entry: SyndEntry): String =
        Option(entry.getDescription)
            .orElse {
                Option(entry.getContents)
                    .filter(_.size > 0)
                    .map(_.get(0))
            }
            .map(_.getValue)
            .getOrElse("")

    private def extractDate(entry: SyndEntry): Option[OffsetDateTime] =
        Option(entry.getPublishedDate)
            .orElse(Option(entry.getUpdatedDate))
            .map(t => OffsetDateTime.ofInstant(t.toInstant, ZoneId))
