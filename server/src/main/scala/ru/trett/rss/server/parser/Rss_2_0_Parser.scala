package ru.trett.server.rss.parser

import ru.trett.rss.server.models.Channel
import ru.trett.rss.server.models.Feed

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import javax.xml.stream.XMLEventReader
import javax.xml.stream.events.EndElement
import javax.xml.stream.events.StartElement
import scala.collection.mutable.ListBuffer
import scala.util.Failure
import scala.util.Success
import scala.util.Try

object Rss_2_0_Parser extends Parser("rss"):

    lazy val dateFormatter: DateTimeFormatter = DateTimeFormatter.RFC_1123_DATE_TIME

    def parse(eventReader: XMLEventReader): Option[Channel] =
        println("Parsing RSS 2.0 feed")
        var title: String = ""
        var link: String = ""
        var hasChannel = false
        val items = ListBuffer[Feed]()
        while (eventReader.hasNext) {
            val event = eventReader.nextEvent()
            event match {
                case el: StartElement =>
                    el.getName().getLocalPart() match {
                        case "channel" => hasChannel = true
                        case "item"    => items += parseItemElement(eventReader)
                        case "title"   => title = parseTitleElement(eventReader)
                        case "link"    => link = parseLinkElement(eventReader)
                        case _         =>
                    }
                case _ => // Handle other event types if necessary
            }
        }
        hasChannel match
            case false => None
            case true  => Some(Channel(0L, title, link, items.toList))

    def parseTitleElement(eventReader: XMLEventReader): String =
        val event = eventReader.nextEvent()
        event match
            case el if el.isCharacters() => el.asCharacters().getData()
            case _                       => ""

    def parseLinkElement(eventReader: XMLEventReader): String =
        val event = eventReader.nextEvent()
        event match
            case el if el.isCharacters() => el.asCharacters().getData()
            case _                       => ""

    def parseItemElement(eventReader: XMLEventReader): Feed =
        var title: String = ""
        var link: String = ""
        var description: String = ""
        var break = false
        var pubDate: Option[OffsetDateTime] = None
        while (eventReader.hasNext && !break) {
            val event = eventReader.nextEvent()
            event match {
                case el: StartElement =>
                    el.getName().getLocalPart() match {
                        case "title" =>
                            val titleEvent = eventReader.nextEvent()
                            title = titleEvent.asCharacters().getData()
                        case "link" =>
                            val linkEvent = eventReader.nextEvent()
                            link = linkEvent.asCharacters().getData()
                        case "description" =>
                            val descEvent = eventReader.nextEvent()
                            description = descEvent.asCharacters().getData()
                        case "pubDate" =>
                            val pubDateEvent = eventReader.nextEvent()
                            val dateStr = pubDateEvent.asCharacters().getData()
                            Try(dateFormatter.parse(dateStr)) match {
                                case Success(date) => pubDate = Some(OffsetDateTime.from(date))
                                case Failure(_) =>
                                    println(s"Failed to parse pubDate: $dateStr")
                                    pubDate = Some(OffsetDateTime.now())
                            }
                        case _ =>
                    }
                case el: EndElement =>
                    if (el.getName().getLocalPart() == "item") {
                        break = true
                    }
                case _ =>
            }
        }
        Feed(link, "", 0L, title, description, pubDate, false)
