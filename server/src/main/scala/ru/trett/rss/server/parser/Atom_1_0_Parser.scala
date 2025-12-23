package ru.trett.rss.server.parser

import ru.trett.rss.server.models.Channel
import ru.trett.rss.server.models.Feed

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
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

class Atom_1_0_Parser[F[_]: Sync: Logger] extends Parser[F]:

    private lazy val formatRfc3339: DateTimeFormatter = new DateTimeFormatterBuilder()
        .appendPattern("yyyy-MM-dd'T'HH:mm:ss")
        .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true)
        .appendPattern("XX")
        .toFormatter()

    private case class FeedState(
        title: String = "",
        hasFeed: Boolean = false,
        entries: ListBuffer[Feed] = ListBuffer.empty
    )

    private case class EntryState(
        title: String = "",
        link: String = "",
        summary: String = "",
        content: String = "",
        updated: Option[OffsetDateTime] = None,
        published: Option[OffsetDateTime] = None
    )

    def parse(eventReader: XMLEventReader, link: String): F[Option[Channel]] =
        @tailrec
        def loop(state: FeedState): FeedState =
            if (!eventReader.hasNext) state
            else
                eventReader.nextEvent() match {
                    case el: StartElement =>
                        el.getName().getLocalPart() match {
                            case "feed" => loop(state.copy(hasFeed = true))
                            case "entry" =>
                                state.entries += parseEntry(eventReader)
                                loop(state)
                            case "title" => loop(state.copy(title = readElementText(eventReader)))
                            case _       => loop(state)
                        }
                    case _ => loop(state)
                }

        for
            _ <- info"Starting to parse Atom 1.0 feed from link: $link"
            finalState <- Sync[F].interruptible(loop(FeedState()))
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
                        val namespace = el.getName().getNamespaceURI()
                        val isAtomNamespace =
                            namespace == "http://www.w3.org/2005/Atom" || namespace == ""

                        if (!isAtomNamespace) {
                            skipElement(eventReader)
                            loop(state)
                        } else {
                            el.getName().getLocalPart() match {
                                case "title" if state.title.isEmpty =>
                                    loop(state.copy(title = readElementText(eventReader)))
                                case "link" =>
                                    val (href, rel) = extractLinkAttributes(el)
                                    if (
                                        state.link.isEmpty && (rel == "alternate" || rel == "self" || rel.isEmpty)
                                    )
                                        loop(state.copy(link = href))
                                    else loop(state)
                                case "summary" =>
                                    loop(state.copy(summary = readElementText(eventReader)))
                                case "content" =>
                                    loop(state.copy(content = readElementText(eventReader)))
                                case "updated" =>
                                    loop(
                                        state
                                            .copy(updated = parseDate(readElementText(eventReader)))
                                    )
                                case "published" =>
                                    loop(
                                        state.copy(published =
                                            parseDate(readElementText(eventReader))
                                        )
                                    )
                                case _ =>
                                    skipElement(eventReader)
                                    loop(state)
                            }
                        }
                    case el: EndElement if el.getName().getLocalPart() == "entry" => state
                    case _                                                        => loop(state)
                }

        val finalState = loop(EntryState())
        val description =
            if (finalState.content.nonEmpty) finalState.content else finalState.summary
        val pubDate =
            finalState.updated.orElse(finalState.published).orElse(Some(OffsetDateTime.now()))
        Feed(finalState.link, "", 0L, finalState.title, description, pubDate, false)

    private def skipElement(eventReader: XMLEventReader): Unit =
        @tailrec
        def loop(depth: Int): Unit =
            if (eventReader.hasNext && depth > 0) {
                eventReader.nextEvent() match {
                    case _: StartElement => loop(depth + 1)
                    case _: EndElement   => loop(depth - 1)
                    case _               => loop(depth)
                }
            }
        loop(1)

    private def readElementText(eventReader: XMLEventReader): String =
        @tailrec
        def loop(depth: Int, textBuffer: StringBuilder): String =
            if (!eventReader.hasNext || depth <= 0) textBuffer.toString().trim()
            else
                eventReader.nextEvent() match {
                    case _: StartElement => loop(depth + 1, textBuffer)
                    case _: EndElement   => loop(depth - 1, textBuffer)
                    case el if el.isCharacters() =>
                        val text = el.asCharacters().getData()
                        if (depth == 1 && text.trim().nonEmpty) loop(depth, textBuffer.append(text))
                        else loop(depth, textBuffer)
                    case _ => loop(depth, textBuffer)
                }

        loop(1, new StringBuilder())

    private def extractLinkAttributes(startElement: StartElement): (String, String) =
        val attributes = startElement.getAttributes()

        @tailrec
        def loop(href: String, rel: String): (String, String) =
            if (!attributes.hasNext()) (href, rel)
            else {
                val attr = attributes.next()
                attr.getName().getLocalPart() match {
                    case "href" => loop(attr.getValue(), rel)
                    case "rel"  => loop(href, attr.getValue())
                    case _      => loop(href, rel)
                }
            }

        loop("", "")

    private def parseDate(dateStr: String): Option[OffsetDateTime] =
        if (dateStr.isEmpty) None
        else
            Try(OffsetDateTime.parse(dateStr, formatRfc3339))
                .orElse(Try(OffsetDateTime.parse(dateStr)))
                .toOption
