package ru.trett.rss.server.parser

import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLEventReader
import javax.xml.stream.events.StartElement
import ru.trett.rss.server.models.Channel

import scala.annotation.tailrec
import cats.effect.IO
import cats.effect.Resource
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.Logger

trait Parser(val root: String):
    def parse(eventReader: XMLEventReader, link: String): Option[Channel]

object Parser:

    private def availableParsers[F[_]: Logger]: List[Parser] =
        List(new Rss_2_0_Parser[F], new Atom_1_0_Parser[F])

    def apply[F[_]: Logger](root: String): Option[Parser] =
        availableParsers[F].find(_.root == root)

    def parseRss(input: fs2.Stream[IO, Byte], link: String)(using
        loggerFactory: LoggerFactory[IO]
    ): IO[Option[Channel]] =

        @tailrec
        def findRootElement(eventReader: XMLEventReader): Option[StartElement] =
            if (!eventReader.hasNext) None
            else
                eventReader.peek() match {
                    case startElement: StartElement => Some(startElement)
                    case _ =>
                        eventReader.nextEvent()
                        findRootElement(eventReader)
                }

        val factory = XMLInputFactory.newInstance()
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        input
            .through(fs2.io.toInputStream)
            .evalMap { is =>
                Resource
                    .make(IO(factory.createXMLEventReader(is)))(reader => IO(reader.close()))
                    .use { eventReader =>
                        IO {
                            for {
                                startElement <- findRootElement(eventReader)
                                logger = LoggerFactory[IO].getLogger
                                parser <- Parser(startElement.getName().getLocalPart())(using
                                    logger
                                )
                                channel <- parser.parse(eventReader, link)
                            } yield channel
                        }
                    }
            }
            .compile
            .last
            .map(_.flatten)
