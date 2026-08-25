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
import ru.trett.rss.server.repositories.FeedRepository

import java.time.OffsetDateTime
import scala.concurrent.duration.DurationInt
import org.http4s.Status

object ChannelService:
    /** Matches `user_channels.folder VARCHAR(100)` in db/init.sql. */
    private val FolderNameMaxLength = 100

class ChannelService(
    channelRepository: ChannelRepository,
    feedRepository: FeedRepository,
    client: Client[IO],
    importanceService: ImportanceService
)(using loggerFactory: LoggerFactory[IO]):

    import ChannelService.FolderNameMaxLength

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
            userChannels <- channelRepository.findUserChannels(user)
            highlightedIds = userChannels.filter(_.highlighted).map(_.channel.id).toSet
            existingByChannel <- channelRepository.getExistingFeedLinksByChannels(
                userChannels.map(_.channel.id),
                user.id
            )
            // Step 1: fetch RSS and insert all feeds immediately so Gemini can't break the cycle
            channelResults <- userChannels.map(_.channel).parTraverse { channel =>
                for {
                    _ <- logger.info(s"Updating channel: ${channel.title}")
                    maybeUpdatedChannel <- getChannel(channel.link).timeout(30.seconds).attempt
                    result <- maybeUpdatedChannel match {
                        case Right(Some(updatedChannel)) =>
                            val existing = existingByChannel.getOrElse(channel.id, Set.empty)
                            val (oldFeeds, rawNewFeeds) =
                                updatedChannel.feedItems.partition(f => existing.contains(f.link))
                            val newFeeds =
                                rawNewFeeds.map(_.copy(channelId = channel.id, userId = user.id))
                            for {
                                _ <- logger.info(
                                    s"Channel ${channel.title}: ${newFeeds.size} new, ${oldFeeds.size} existing"
                                )
                                n <- channelRepository.insertFeeds(
                                    oldFeeds ++ newFeeds,
                                    channel.id,
                                    user.id
                                )
                            } yield (n, newFeeds)
                        case Right(None) =>
                            logger.error(
                                s"Failed to update channel. ${channel.title}. Response is empty."
                            ) *> IO.pure((0, Nil))
                        case Left(error) =>
                            logger.error(error)(s"Failed to update channel: ${channel.title}") *> IO
                                .pure((0, Nil))
                    }
                } yield result
            }
            allNewFeeds = channelResults.flatMap(_._2)
            // Step 2: score new unread feeds and update the DB (separate step, inserts are already safe)
            _ <-
                if allNewFeeds.nonEmpty then
                    logger.info(s"[Importance] Scoring ${allNewFeeds.size} new feeds") *>
                        importanceService
                            .score(user, highlightedIds, allNewFeeds)
                            .flatTap(scored =>
                                val important = scored.count(_.important)
                                val autoRead = scored.count(f => f.isRead && !f.important)
                                logger.info(
                                    s"[Importance] Result: $important important, $autoRead auto-read, ${scored.size - important - autoRead} unclassified"
                                )
                            )
                            .flatMap(feedRepository.updateFeedImportance)
                            .handleErrorWith(e =>
                                logger.warn(s"[Importance] Scoring step failed: ${e.getMessage}")
                            )
                else IO.unit
        } yield channelResults.map(_._1)

    private def getChannel(link: String): IO[Option[Channel]] =
        for {
            url <- IO.fromEither(Uri.fromString(link)).handleErrorWith { error =>
                logger.error(error)(s"Invalid URI: $link") *> IO.raiseError(error)
            }
            channel <-
                client
                    .get[Option[Channel]](url) {
                        case Status.Successful(r) =>
                            Parser
                                .parse(r.body, link)
                                .flatMap {
                                    case Right(channel) => IO.pure(Some(channel))
                                    case Left(error) =>
                                        logger.error(error)(s"Failed to parse feed: $link") *> IO
                                            .pure(None)
                                }
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
        channelRepository.findUserChannels(user).map {
            _.map { uc =>
                ChannelData(
                    uc.channel.id,
                    uc.channel.title,
                    uc.channel.link,
                    uc.highlighted,
                    uc.folder
                )
            }
        }

    def getChannelsAndFeeds(
        user: User,
        page: Int,
        limit: Int,
        importantOnly: Boolean,
        hideRead: Boolean
    ): IO[List[FeedItemData]] =
        val offset = (page - 1) * limit
        val channels =
            channelRepository.getChannelsWithFeedsByUser(
                user,
                limit,
                offset,
                importantOnly,
                hideRead
            )
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
                        highlighted,
                        feed.imageUrl,
                        feed.important
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

    /** A blank name clears the folder rather than creating one called "". Truncated to the column
      * width so an over-long name is a no-op rather than a database error.
      */
    def updateChannelFolder(id: Long, user: User, folder: Option[String]): IO[Int] =
        val name = folder.map(_.trim).filter(_.nonEmpty).map(_.take(FolderNameMaxLength))
        channelRepository.updateChannelFolder(id, user, name)
