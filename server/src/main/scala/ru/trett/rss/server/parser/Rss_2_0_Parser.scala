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

    private val MediaNs = "http://search.yahoo.com/mrss/"
    private val ContentNs = "http://purl.org/rss/1.0/modules/content/"
    private val ItunesNs = "http://www.itunes.com/dtds/podcast-1.0.dtd"

    private case class FeedState(
        title: String = "",
        hasFeed: Boolean = false,
        entries: ArrayBuffer[Feed] = ArrayBuffer.empty
    )

    private case class EntryState(
        title: String = "",
        link: String = "",
        description: String = "",
        contentEncoded: String = "",
        pubDate: Option[OffsetDateTime] = None,
        imageUrl: Option[String] = None,
        categories: List[String] = List.empty
    )

    private[parser] def parse(
        reader: XMLEventReader,
        link: String
    ): F[Either[ParserError, Channel]] = {
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
                        val ns = el.getName.getNamespaceURI
                        val localPart = el.getName.getLocalPart
                        if (ns == MediaNs) {
                            localPart match {
                                case "content" =>
                                    val url = attrValue(el, "url")
                                    val medium = attrValue(el, "medium")
                                    val tpe = attrValue(el, "type")
                                    skipElement(reader)
                                    if (
                                        state.imageUrl.isEmpty && url.nonEmpty &&
                                        (medium == "image" || tpe.startsWith("image/"))
                                    )
                                        loop(state.copy(imageUrl = Some(url)))
                                    else loop(state)
                                case "thumbnail" =>
                                    val url = attrValue(el, "url")
                                    skipElement(reader)
                                    if (state.imageUrl.isEmpty && url.nonEmpty)
                                        loop(state.copy(imageUrl = Some(url)))
                                    else loop(state)
                                case _ =>
                                    skipElement(reader)
                                    loop(state)
                            }
                        } else if (ns == ItunesNs) {
                            localPart match {
                                case "image" =>
                                    val href = attrValue(el, "href")
                                    skipElement(reader)
                                    if (state.imageUrl.isEmpty && href.nonEmpty)
                                        loop(state.copy(imageUrl = Some(href)))
                                    else loop(state)
                                case _ =>
                                    skipElement(reader)
                                    loop(state)
                            }
                        } else if (ns == ContentNs) {
                            localPart match {
                                case "encoded" =>
                                    loop(state.copy(contentEncoded = readElementText(reader)))
                                case _ =>
                                    skipElement(reader)
                                    loop(state)
                            }
                        } else {
                            localPart match {
                                case "title" =>
                                    loop(state.copy(title = readElementText(reader)))
                                case "link" =>
                                    loop(state.copy(link = readElementText(reader)))
                                case "description" =>
                                    loop(state.copy(description = readElementText(reader)))
                                case "pubDate" =>
                                    val dateStr = readElementText(reader)
                                    val pubDate =
                                        Try(
                                            OffsetDateTime.from(dateFormatter.parse(dateStr))
                                        ).toOption
                                            .orElse(Some(OffsetDateTime.now()))
                                    loop(state.copy(pubDate = pubDate))
                                case "enclosure" =>
                                    val url = attrValue(el, "url")
                                    val tpe = attrValue(el, "type")
                                    skipElement(reader)
                                    if (
                                        state.imageUrl.isEmpty && url.nonEmpty &&
                                        tpe.startsWith("image/")
                                    )
                                        loop(state.copy(imageUrl = Some(url)))
                                    else loop(state)
                                case "category" =>
                                    val cat = readElementText(reader).trim
                                    if (cat.nonEmpty)
                                        loop(state.copy(categories = state.categories :+ cat))
                                    else loop(state)
                                case _ => loop(state)
                            }
                        }
                    case el: EndElement if el.getName.getLocalPart == "item" => state
                    case _                                                   => loop(state)
                }
            }
        }

        val finalState = loop(EntryState())
        val imageUrl = finalState.imageUrl
            .orElse(ImageExtractor.firstImageFromHtml(finalState.contentEncoded))
            .orElse(ImageExtractor.firstImageFromHtml(finalState.description))
        Feed(
            finalState.link,
            "",
            0L,
            finalState.title,
            finalState.description,
            finalState.pubDate,
            imageUrl = imageUrl,
            categories = finalState.categories.distinct
        )
    }

    private def attrValue(el: StartElement, name: String): String = {
        val attrs = el.getAttributes
        @tailrec
        def loop(): String =
            if (!attrs.hasNext) ""
            else {
                val attr = attrs.next()
                if (attr.getName.getLocalPart == name) attr.getValue else loop()
            }
        loop()
    }

    private def skipElement(reader: XMLEventReader): Unit = {
        @tailrec
        def loop(depth: Int): Unit =
            if (reader.hasNext && depth > 0) {
                reader.nextEvent() match {
                    case _: StartElement => loop(depth + 1)
                    case _: EndElement   => loop(depth - 1)
                    case _               => loop(depth)
                }
            }
        loop(1)
    }

    private def readElementText(reader: XMLEventReader): String = {
        @tailrec
        def loop(builder: StringBuilder): String = {
            if (!reader.hasNext) builder.toString()
            else {
                val event = reader.nextEvent
                if (event.isCharacters) {
                    loop(builder.append(event.asCharacters.getData))
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
