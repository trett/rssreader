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

class Atom_1_0_Parser[F[_]: Sync: Logger] extends FeedParser[F, XMLEventReader]:

    private lazy val formatRfc3339: DateTimeFormatter = new DateTimeFormatterBuilder()
        .appendPattern("yyyy-MM-dd'T'HH:mm:ss")
        .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true)
        .appendPattern("XX")
        .toFormatter()

    private val MediaNs = "http://search.yahoo.com/mrss/"
    private val ItunesNs = "http://www.itunes.com/dtds/podcast-1.0.dtd"

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
        published: Option[OffsetDateTime] = None,
        imageUrl: Option[String] = None
    )

    private[parser] def parse(
        eventReader: XMLEventReader,
        link: String
    ): F[Either[ParserError, Channel]] =
        @tailrec
        def loop(state: FeedState): FeedState =
            if (!eventReader.hasNext) state
            else
                eventReader.nextEvent() match {
                    case el: StartElement =>
                        el.getName.getLocalPart match {
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
            _ <-
                info"Parsed ${finalState.entries.length} entries from Atom 1.0 feed: ${finalState.title}"
            result =
                if (finalState.hasFeed)
                    Right(Channel(0L, finalState.title, link, finalState.entries.toList))
                else Left(ParserError.InvalidFeed("Missing <feed> element"))
        yield result

    private def parseEntry(eventReader: XMLEventReader): Feed =
        @tailrec
        def loop(state: EntryState): EntryState =
            if (!eventReader.hasNext) state
            else
                eventReader.nextEvent() match {
                    case el: StartElement =>
                        val namespace = el.getName.getNamespaceURI
                        val localPart = el.getName.getLocalPart

                        if (namespace == MediaNs) {
                            localPart match {
                                case "content" =>
                                    val url = attrValue(el, "url")
                                    val medium = attrValue(el, "medium")
                                    val tpe = attrValue(el, "type")
                                    skipElement(eventReader)
                                    if (
                                        state.imageUrl.isEmpty && url.nonEmpty &&
                                        (medium == "image" || tpe.startsWith("image/"))
                                    )
                                        loop(state.copy(imageUrl = Some(url)))
                                    else loop(state)
                                case "thumbnail" =>
                                    val url = attrValue(el, "url")
                                    skipElement(eventReader)
                                    if (state.imageUrl.isEmpty && url.nonEmpty)
                                        loop(state.copy(imageUrl = Some(url)))
                                    else loop(state)
                                case _ =>
                                    skipElement(eventReader)
                                    loop(state)
                            }
                        } else if (namespace == ItunesNs) {
                            localPart match {
                                case "image" =>
                                    val href = attrValue(el, "href")
                                    skipElement(eventReader)
                                    if (state.imageUrl.isEmpty && href.nonEmpty)
                                        loop(state.copy(imageUrl = Some(href)))
                                    else loop(state)
                                case _ =>
                                    skipElement(eventReader)
                                    loop(state)
                            }
                        } else {
                            val isAtomNamespace =
                                namespace == "http://www.w3.org/2005/Atom" || namespace == ""

                            if (!isAtomNamespace) {
                                skipElement(eventReader)
                                loop(state)
                            } else {
                                localPart match {
                                    case "title" if state.title.isEmpty =>
                                        loop(state.copy(title = readElementText(eventReader)))
                                    case "link" =>
                                        val (href, rel, tpe) = extractLinkAttributes(el)
                                        if (
                                            state.link.isEmpty && (rel == "alternate" || rel == "self" || rel.isEmpty)
                                        )
                                            loop(state.copy(link = href))
                                        else if (
                                            state.imageUrl.isEmpty && rel == "enclosure" &&
                                            tpe.startsWith("image/") && href.nonEmpty
                                        )
                                            loop(state.copy(imageUrl = Some(href)))
                                        else loop(state)
                                    case "summary" =>
                                        loop(state.copy(summary = readElementText(eventReader)))
                                    case "content" =>
                                        loop(state.copy(content = readElementText(eventReader)))
                                    case "updated" =>
                                        loop(
                                            state
                                                .copy(updated =
                                                    parseDate(readElementText(eventReader))
                                                )
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
                        }
                    case el: EndElement if el.getName.getLocalPart == "entry" => state
                    case _                                                    => loop(state)
                }

        val finalState = loop(EntryState())
        val description =
            if (finalState.content.nonEmpty) finalState.content else finalState.summary
        val pubDate =
            finalState.updated.orElse(finalState.published).orElse(Some(OffsetDateTime.now()))
        val imageUrl = finalState.imageUrl
            .orElse(ImageExtractor.firstImageFromHtml(finalState.content))
            .orElse(ImageExtractor.firstImageFromHtml(finalState.summary))
        Feed(finalState.link, "", 0L, finalState.title, description, pubDate, false, imageUrl)

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
                    case el if el.isCharacters =>
                        val text = el.asCharacters().getData
                        if (depth == 1 && text.trim().nonEmpty) loop(depth, textBuffer.append(text))
                        else loop(depth, textBuffer)
                    case _ => loop(depth, textBuffer)
                }

        loop(1, new StringBuilder())

    private def attrValue(el: StartElement, name: String): String =
        val attrs = el.getAttributes
        @tailrec
        def loop(): String =
            if (!attrs.hasNext) ""
            else
                val attr = attrs.next()
                if (attr.getName.getLocalPart == name) attr.getValue else loop()
        loop()

    private def extractLinkAttributes(startElement: StartElement): (String, String, String) =
        val attributes = startElement.getAttributes

        @tailrec
        def loop(href: String, rel: String, tpe: String): (String, String, String) =
            if (!attributes.hasNext) (href, rel, tpe)
            else {
                val attr = attributes.next()
                attr.getName.getLocalPart match {
                    case "href" => loop(attr.getValue, rel, tpe)
                    case "rel"  => loop(href, attr.getValue, tpe)
                    case "type" => loop(href, rel, attr.getValue)
                    case _      => loop(href, rel, tpe)
                }
            }

        loop("", "", "")

    private def parseDate(dateStr: String): Option[OffsetDateTime] =
        if (dateStr.isEmpty) None
        else
            Try(OffsetDateTime.parse(dateStr, formatRfc3339))
                .orElse(Try(OffsetDateTime.parse(dateStr)))
                .toOption

object Atom_1_0_Parser:
    def make[F[_]: Sync: Logger]: FeedParser[F, XMLEventReader] =
        new Atom_1_0_Parser[F]
