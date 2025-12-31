package ru.trett.rss.server.parser

import ru.trett.rss.server.models.Channel
import ru.trett.rss.server.models.Feed

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import javax.xml.stream.XMLEventReader
import javax.xml.stream.events.{EndElement, StartElement}

import scala.annotation.tailrec
import scala.util.Try
import org.typelevel.log4cats.Logger
import cats.effect.Sync
import cats.syntax.all._
import scala.collection.mutable.ArrayBuffer

class Rss_2_0_Parser[F[_]: Sync: Logger] extends FeedParser[F, XMLEventReader] {
    private lazy val dateFormatter: DateTimeFormatter = DateTimeFormatter.RFC_1123_DATE_TIME

    private case class FeedState(
        title: String = "",
        hasFeed: Boolean = false,
        entries: ArrayBuffer[Feed] = ArrayBuffer.empty
    )

    private case class EntryState(
        title: String = "",
        link: String = "",
        description: String = "",
        pubDate: Option[OffsetDateTime] = None
    )

    def parse(reader: XMLEventReader, link: String): F[Either[ParserError, Channel]] = {
        @tailrec
        def loop(state: FeedState): FeedState = {
            if (!reader.hasNext) state
            else {
                reader.nextEvent() match {
                    case el: StartElement =>
                        el.getName.getLocalPart match {
                            case "channel" => loop(state.copy(hasFeed = true))
                            case "item" =>
                                state.entries += parseEntry(reader)
                                loop(state)
                            case "title" =>
                                loop(state.copy(title = readElementText(reader)))
                            case _ => loop(state)
                        }
                    case _ => loop(state)
                }
            }
        }

        for {
            _ <- Logger[F].info(s"Starting to parse RSS 2.0 feed from link: $link")
            finalState <- Sync[F].delay(loop(FeedState()))
            _ <- Logger[F].info(
                s"Parsed ${finalState.entries.length} items from RSS 2.0 feed: ${finalState.title}"
            )
        } yield {
            if (finalState.hasFeed)
                Right(Channel(0L, finalState.title, link, finalState.entries.toList))
            else Left(ParserError.InvalidFeed("Missing <channel> element"))
        }
    }

    private def parseEntry(reader: XMLEventReader): Feed = {
        @tailrec
        def loop(state: EntryState): EntryState = {
            if (!reader.hasNext) state
            else {
                reader.nextEvent() match {
                    case el: StartElement =>
                        el.getName.getLocalPart match {
                            case "title" =>
                                loop(state.copy(title = readElementText(reader)))
                            case "link" =>
                                loop(state.copy(link = readElementText(reader)))
                            case "description" =>
                                loop(state.copy(description = readElementText(reader)))
                            case "pubDate" =>
                                val dateStr = readElementText(reader)
                                val pubDate =
                                    Try(OffsetDateTime.from(dateFormatter.parse(dateStr))).toOption
                                        .orElse(Some(OffsetDateTime.now()))
                                loop(state.copy(pubDate = pubDate))
                            case _ => loop(state)
                        }
                    case el: EndElement if el.getName.getLocalPart == "item" => state
                    case _                                                   => loop(state)
                }
            }
        }

        val finalState = loop(EntryState())
        Feed(
            finalState.link,
            "",
            0L,
            finalState.title,
            finalState.description,
            finalState.pubDate,
            false
        )
    }

    private def readElementText(reader: XMLEventReader): String = {
        @tailrec
        def loop(builder: StringBuilder): String = {
            if (!reader.hasNext) builder.toString()
            else {
                val event = reader.nextEvent()
                if (event.isCharacters) {
                    loop(builder.append(event.asCharacters().getData))
                } else if (event.isEndElement) {
                    builder.toString()
                } else {
                    loop(builder)
                }
            }
        }
        loop(new StringBuilder)
    }
}

object Rss_2_0_Parser {
    def make[F[_]: Sync: Logger]: FeedParser[F, XMLEventReader] =
        new Rss_2_0_Parser[F]
}
