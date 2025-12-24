package ru.trett.rss.server.parser

import ru.trett.rss.server.models.Channel
import ru.trett.rss.server.models.Feed

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import javax.xml.stream.XMLEventReader
import javax.xml.stream.events.EndElement
import javax.xml.stream.events.StartElement

import scala.annotation.tailrec
import scala.util.Try
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.syntax.*
import cats.effect.Sync
import cats.syntax.all.*
import scala.collection.mutable.ListBuffer

class Rss_2_0_Parser[F[_]: Sync: Logger] extends Parser[F]:

    private lazy val dateFormatter: DateTimeFormatter = DateTimeFormatter.RFC_1123_DATE_TIME

    private case class FeedState(
        title: String = "",
        hasFeed: Boolean = false,
        entries: ListBuffer[Feed] = ListBuffer.empty
    )

    private case class EntryState(
        title: String = "",
        link: String = "",
        description: String = "",
        pubDate: Option[OffsetDateTime] = None
    )

    def parse(eventReader: XMLEventReader, link: String): F[Option[Channel]] =
        @tailrec
        def loop(state: FeedState): FeedState =
            if (!eventReader.hasNext) state
            else
                eventReader.nextEvent() match {
                    case el: StartElement =>
                        el.getName().getLocalPart() match {
                            case "channel" => loop(state.copy(hasFeed = true))
                            case "item" =>
                                state.entries += parseEntry(eventReader)
                                loop(state)
                            case "title" => loop(state.copy(title = readElementText(eventReader)))
                            case _       => loop(state)
                        }
                    case _ => loop(state)
                }

        for
            _ <- info"Starting to parse RSS 2.0 feed from link: $link"
            finalState <- Sync[F].interruptible(loop(FeedState()))
            _ <-
                info"Parsed ${finalState.entries.length} items from RSS 2.0 feed: ${finalState.title}"
            result = Option.when(finalState.hasFeed)(
                Channel(0L, finalState.title, link, finalState.entries.toList)
            )
        yield result

    private def parseEntry(eventReader: XMLEventReader): Feed =
        @tailrec
        def loop(state: EntryState): EntryState =
            if (!eventReader.hasNext) state
            else
                eventReader.nextEvent() match {
                    case el: StartElement =>
                        el.getName().getLocalPart() match {
                            case "title" =>
                                loop(state.copy(title = readElementText(eventReader)))
                            case "link" =>
                                loop(state.copy(link = readElementText(eventReader)))
                            case "description" =>
                                loop(state.copy(description = readElementText(eventReader)))
                            case "pubDate" =>
                                val dateStr = readElementText(eventReader)
                                val pubDate = Try(OffsetDateTime.from(dateFormatter.parse(dateStr)))
                                    .getOrElse(OffsetDateTime.now())
                                loop(state.copy(pubDate = Some(pubDate)))
                            case _ => loop(state)
                        }
                    case el: EndElement if el.getName().getLocalPart() == "item" => state
                    case _                                                       => loop(state)
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

    private def readElementText(eventReader: XMLEventReader): String =
        @tailrec
        def loop(builder: StringBuilder): String =
            if (!eventReader.hasNext) builder.toString()
            else
                val event = eventReader.nextEvent()
                if (event.isCharacters()) {
                    loop(builder.append(event.asCharacters().getData()))
                } else if (event.isEndElement()) {
                    builder.toString()
                } else {
                    loop(builder)
                }

        loop(new StringBuilder)
