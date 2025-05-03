package ru.trett.rss.server.services

import cats.effect.IO
import cats.effect.kernel.Resource
import cats.syntax.all.*
import com.rometools.rome.feed.synd.SyndEntry
import com.rometools.rome.io.SyndFeedInput
import com.rometools.rome.io.XmlReader
import org.http4s.Uri
import org.http4s.client.Client
import ru.trett.rss.models.ChannelData
import ru.trett.rss.models.FeedItemData
import ru.trett.rss.server.models.Channel
import ru.trett.rss.server.models.Feed
import ru.trett.rss.server.models.User
import ru.trett.rss.server.repositories.ChannelRepository
import scala.concurrent.duration.DurationInt

import java.time.OffsetDateTime
import scala.jdk.CollectionConverters.*
import org.typelevel.log4cats.Logger

class ChannelService(channelRepository: ChannelRepository, client: Client[IO])(using
    logger: Logger[IO]
):

    private val ZoneId = java.time.ZoneId.systemDefault()

    def createChannel(link: String, user: User): IO[Long] =
        for {
            channel <- getChannel(link)
            result <- channel match {
                case Some(value) => channelRepository.insertChannel(value, user)
                case None        => IO(0L)
            }
        } yield result

    def updateFeeds(user: User): IO[List[Int]] =
        for {
            channels <- channelRepository.findUserChannels(user)
            result <- channels.parTraverse { channel =>
                for {
                    _ <- logger.info(s"Updating channel: ${channel.title}")
                    maybeUpdatedChannel <- getChannel(channel.link).timeout(30.seconds)
                    rows <- maybeUpdatedChannel
                        .map(updatedChannel =>
                            channelRepository.insertFeeds(updatedChannel.feedItems, channel.id)
                        )
                        .getOrElse {
                            logger.error(s"Failed to update channel: ${channel.title}")
                                *> IO.pure(0)
                        }
                } yield rows
            }
        } yield result

    private def getChannel(link: String): IO[Option[Channel]] =
        for {
            url <- IO.fromEither(Uri.fromString(link)).handleErrorWith { error =>
                logger.error(error)(s"Invalid URI: $link") *> IO.raiseError(error)
            }
            channel <-
                client
                    .get(url) { response =>
                        response.body.compile.to(Array).flatMap { bytes =>
                            Resource
                                .fromAutoCloseable(
                                    IO(new XmlReader(new java.io.ByteArrayInputStream(bytes)))
                                )
                                .use { reader =>
                                    parse(reader, link).handleErrorWith { error =>
                                        logger.error(error)(
                                            s"Failed to parse the feed: $link"
                                        ) *> IO.none
                                    }
                                }
                        }
                    }
        } yield channel

    private def parse(reader: XmlReader, link: String): IO[Option[Channel]] = {
        for {
            _ <- logger.info(s"Starting to parse RSS feed: $link")
            input = new SyndFeedInput()
            syndFeed <- IO.interruptible(input.build(reader))
            title = syndFeed.getTitle
            _ <- logger.info(s"Parsing the channel: title '$title', type '${syndFeed.getFeedType}'")

            feedItems = syndFeed.getEntries.asScala.map { entry =>
                Feed(
                    channelId = 0L, // This will be set after channel creation
                    title = entry.getTitle,
                    link = entry.getLink,
                    description = extractDescription(entry),
                    pubDate = extractDate(entry)
                )
            }.toList

            channel = Channel(id = 0L, title = title, link = link, feedItems = feedItems)

            _ <- logger.info(s"Parsed ${feedItems.size} items")
            _ <- logger.info("End of parsing the channel")
        } yield Some(channel)
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

    def getChannels(user: User): IO[List[ChannelData]] =
        channelRepository.findUserChannels(user).flatMap {
            _.traverse { channel =>
                IO.pure(ChannelData(channel.id, channel.title, channel.link))
            }
        }

    def getChannelsAndFeeds(user: User): IO[List[FeedItemData]] =
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

    def removeChannel(id: Long, user: User): IO[Long] =
        channelRepository.deleteChannel(id, user).flatMap {
            case 0 => IO.raiseError(Exception("Channel does not belong to the user"))
            case _ => IO.pure(id)
        }
