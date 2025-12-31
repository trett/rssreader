package ru.trett.rss.server.services

import cats.effect.IO
import cats.syntax.all.*
import org.http4s.Uri
import org.http4s.client.Client
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.LoggerFactory
import ru.trett.rss.models.ChannelData
import ru.trett.rss.models.FeedItemData
import ru.trett.rss.server.models.Channel
import ru.trett.rss.server.models.User
import ru.trett.rss.server.parser.FeedParserRegistry
import ru.trett.rss.server.parser.Parser
import ru.trett.rss.server.repositories.ChannelRepository

import java.time.OffsetDateTime
import scala.concurrent.duration.DurationInt
import org.http4s.Status

class ChannelService(channelRepository: ChannelRepository, client: Client[IO])(using
    loggerFactory: LoggerFactory[IO]
):

    private val logger: Logger[IO] = LoggerFactory[IO].getLogger
    given Logger[IO] = logger
    given FeedParserRegistry[IO] = FeedParserRegistry.default[IO]

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
            channels <- channelRepository.findUserChannelsWithHighlight(user)
            result <- channels.parTraverse { (channel, _) =>
                for {
                    _ <- logger.info(s"Updating channel: ${channel.title}")
                    maybeUpdatedChannel <- getChannel(channel.link).timeout(30.seconds).attempt
                    rows <- maybeUpdatedChannel match {
                        case Right(Some(updatedChannel)) =>
                            channelRepository.insertFeeds(
                                updatedChannel.feedItems,
                                channel.id,
                                user.id
                            )
                        case Right(None) =>
                            logger.error(
                                s"Failed to update channel. ${channel.title}. Response is empty."
                            ) *> IO.pure(0)
                        case Left(error) =>
                            logger.error(error)(s"Failed to update channel: ${channel.title}") *> IO
                                .pure(0)
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
                    .get[Option[Channel]](url) {
                        case Status.Successful(r) => Parser.parseRss(r.body, link)
                        case r =>
                            r.as[String]
                                .map(b =>
                                    logger.error(
                                        s"Request failed with status ${r.status.code} and body $b"
                                    )
                                ) *> IO.pure(None)
                    }
        } yield channel

    def getChannels(user: User): IO[List[ChannelData]] =
        channelRepository.findUserChannelsWithHighlight(user).flatMap {
            _.traverse { case (channel, highlighted) =>
                IO.pure(ChannelData(channel.id, channel.title, channel.link, highlighted))
            }
        }

    def getChannelsAndFeeds(user: User, page: Int, limit: Int): IO[List[FeedItemData]] =
        val offset = (page - 1) * limit
        val channels = channelRepository.getChannelsWithFeedsByUser(user, limit, offset)
        channels.flatMap {
            _.traverse { case (channel, feed, highlighted) =>
                IO.pure(
                    FeedItemData(
                        feed.link,
                        channel.title,
                        feed.title,
                        feed.description,
                        feed.pubDate.getOrElse(OffsetDateTime.now()),
                        feed.isRead,
                        highlighted
                    )
                )
            }
        }

    def removeChannel(id: Long, user: User): IO[Long] =
        channelRepository.deleteChannel(id, user).flatMap {
            case 0 => IO.raiseError(Exception("Channel does not belong to the user"))
            case _ => IO.pure(id)
        }

    def updateChannelHighlight(id: Long, user: User, highlighted: Boolean): IO[Int] =
        channelRepository.updateChannelHighlight(id, user, highlighted)
