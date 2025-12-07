package ru.trett.server.rss.parser

import java.io.InputStream
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.events.StartElement
import ru.trett.rss.server.models.Channel
import javax.xml.stream.XMLEventReader


trait Parser(val root: String):
    def parse(eventReader: XMLEventReader): Option[Channel]

object Parser:

    def getParser(root: String): Option[Parser] =
        root match
            case Rss_2_0_Parser.root   => Some(Rss_2_0_Parser)
            case Atom_1_0_Parser.root  => Some(Atom_1_0_Parser)
            case _                     => None

    def parseRss(input: InputStream): Option[Channel] =
        val factory = XMLInputFactory.newInstance()
        val eventReader = factory.createXMLEventReader(input)

        // Skip to the first StartElement
        var rootElement: Option[StartElement] = None
        while (eventReader.hasNext && rootElement.isEmpty) {
            val event = eventReader.peek()
            event match {
                case startElement: StartElement =>
                    rootElement = Some(startElement)
                case _ =>
                    eventReader.nextEvent() // Skip non-StartElement events
            }
        }

        val parser = rootElement match {
            case Some(startElement) =>
                val root = startElement.getName().getLocalPart()
                getParser(root)
            case None =>
                println("No root element found")
                None
        }
        parser.map(_.parse(eventReader)).getOrElse(None)
        
