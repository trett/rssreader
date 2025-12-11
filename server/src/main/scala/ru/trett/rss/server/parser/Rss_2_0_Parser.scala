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

object Rss_2_0_Parser extends Parser("rss"):

    private lazy val dateFormatter: DateTimeFormatter = DateTimeFormatter.RFC_1123_DATE_TIME

    private case class ChannelState(
        title: String = "",
        hasChannel: Boolean = false,
        items: List[Feed] = Nil
    )

    private case class ItemState(
        title: String = "",
        link: String = "",
        description: String = "",
        pubDate: Option[OffsetDateTime] = None
    )

    def parse(eventReader: XMLEventReader, link: String): Option[Channel] =
        @tailrec
        def loop(state: ChannelState): ChannelState =
            if (!eventReader.hasNext) state
            else
                eventReader.nextEvent() match {
                    case el: StartElement =>
                        el.getName().getLocalPart() match {
                            case "channel" => loop(state.copy(hasChannel = true))
                            case "item" =>
                                loop(
                                    state.copy(items = state.items :+ parseItemElement(eventReader))
                                )
                            case "title" => loop(state.copy(title = parseTitleElement(eventReader)))
                            case _       => loop(state)
                        }
                    case _ => loop(state)
                }

        val finalState = loop(ChannelState())
        Option.when(finalState.hasChannel)(Channel(0L, finalState.title, link, finalState.items))

    private def parseTitleElement(eventReader: XMLEventReader): String =
        val event = eventReader.nextEvent()
        if (event.isCharacters()) event.asCharacters().getData() else ""

    private def parseItemElement(eventReader: XMLEventReader): Feed =
        @tailrec
        def loop(state: ItemState): ItemState =
            if (!eventReader.hasNext) state
            else
                eventReader.nextEvent() match {
                    case el: StartElement =>
                        el.getName().getLocalPart() match {
                            case "title" =>
                                val titleEvent = eventReader.nextEvent()
                                loop(state.copy(title = titleEvent.asCharacters().getData()))
                            case "link" =>
                                val linkEvent = eventReader.nextEvent()
                                loop(state.copy(link = linkEvent.asCharacters().getData()))
                            case "description" =>
                                val descEvent = eventReader.nextEvent()
                                loop(state.copy(description = descEvent.asCharacters().getData()))
                            case "pubDate" =>
                                val pubDateEvent = eventReader.nextEvent()
                                val dateStr = pubDateEvent.asCharacters().getData()
                                val pubDate = Try(OffsetDateTime.from(dateFormatter.parse(dateStr)))
                                    .getOrElse(OffsetDateTime.now())
                                loop(state.copy(pubDate = Some(pubDate)))
                            case _ => loop(state)
                        }
                    case el: EndElement if el.getName().getLocalPart() == "item" => state
                    case _                                                       => loop(state)
                }

        val finalState = loop(ItemState())
        Feed(
            finalState.link,
            "",
            0L,
            finalState.title,
            finalState.description,
            finalState.pubDate,
            false
        )
