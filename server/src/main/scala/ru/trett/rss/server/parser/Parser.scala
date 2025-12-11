package ru.trett.rss.server.parser

import java.io.InputStream
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLEventReader
import javax.xml.stream.events.StartElement
import ru.trett.rss.server.models.Channel

import scala.annotation.tailrec

trait Parser(val root: String):
    def parse(eventReader: XMLEventReader, link: String): Option[Channel]

object Parser:

    def getParser(root: String): Option[Parser] =
        root match
            case Rss_2_0_Parser.root  => Some(Rss_2_0_Parser)
            case Atom_1_0_Parser.root => Some(Atom_1_0_Parser)
            case _                    => None

    def parseRss(input: InputStream, link: String): Option[Channel] =
        val factory = XMLInputFactory.newInstance()
        val eventReader = factory.createXMLEventReader(input)

        @tailrec
        def findRootElement(): Option[StartElement] =
            if (!eventReader.hasNext) None
            else
                eventReader.peek() match {
                    case startElement: StartElement => Some(startElement)
                    case _ =>
                        eventReader.nextEvent()
                        findRootElement()
                }

        val parser = findRootElement().flatMap { startElement =>
            getParser(startElement.getName().getLocalPart())
        }
        parser.flatMap(_.parse(eventReader, link))
