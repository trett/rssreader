package ru.trett.rss.server.parser

import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLEventReader
import javax.xml.stream.events.StartElement
import ru.trett.rss.server.models.Channel

import scala.annotation.tailrec
import cats.effect.IO
import cats.effect.Resource
import cats.effect.Sync
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.Logger

trait Parser[F[_]]:
    def parse(eventReader: XMLEventReader, link: String): F[Option[Channel]]

enum ParserType(val rootElement: String):
    case Rss20 extends ParserType("rss")
    case Atom10 extends ParserType("feed")

    def createParser[F[_]: Sync: Logger]: Parser[F] = this match
        case Rss20  => new Rss_2_0_Parser[F]
        case Atom10 => new Atom_1_0_Parser[F]

object ParserType:
    def fromRoot(root: String): Option[ParserType] =
        ParserType.values.find(_.rootElement == root)

object Parser:

    private val xmlInputFactory: XMLInputFactory =
        val factory = XMLInputFactory.newInstance()
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false)
        factory

    def apply[F[_]: Sync: Logger](root: String): Option[Parser[F]] =
        ParserType.fromRoot(root).map(_.createParser[F])

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
        val logger = LoggerFactory[IO].getLogger

        input
            .through(fs2.io.toInputStream)
            .evalMap { is =>
                Resource
                    .fromAutoCloseable(IO.blocking(is))
                    .use { inputStream =>
                        Resource
                            .make(IO.blocking(xmlInputFactory.createXMLEventReader(inputStream)))(
                                reader => IO.blocking(reader.close()).handleErrorWith(_ => IO.unit)
                            )
                            .use { eventReader =>
                                for {
                                    startElement <- IO.blocking(findRootElement(eventReader))
                                    channel <- startElement match
                                        case Some(el) =>
                                            given Logger[IO] = LoggerFactory[IO].getLogger
                                            Parser[IO](el.getName().getLocalPart()) match
                                                case Some(parser) => parser.parse(eventReader, link)
                                                case None         => IO.pure(None)
                                        case None => IO.pure(None)
                                } yield channel
                            }
                    }
            }
            .compile
            .last
            .map(_.flatten)
