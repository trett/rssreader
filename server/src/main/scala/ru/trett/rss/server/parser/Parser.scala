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
import java.io.InputStream

trait Parser[F[_]](val root: String):
    def parse(eventReader: XMLEventReader, link: String): F[Option[Channel]]

object Parser:

    private val xmlInputFactory: XMLInputFactory =
        val factory = XMLInputFactory.newInstance()
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false)
        factory

    private def availableParsers[F[_]: Sync: Logger]: List[Parser[F]] =
        List(new Rss_2_0_Parser[F], new Atom_1_0_Parser[F])

    def apply[F[_]: Sync: Logger](root: String): Option[Parser[F]] =
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

        def createResources(is: InputStream): Resource[IO, XMLEventReader] =
            Resource.make(IO.blocking(xmlInputFactory.createXMLEventReader(is)))(reader =>
                IO.blocking(reader.close()).handleErrorWith(_ => IO.unit)
            )

        input
            .through(fs2.io.toInputStream)
            .evalMap { is =>
                Resource
                    .make(IO.pure(is))(inputStream =>
                        IO.blocking(inputStream.close()).handleErrorWith(_ => IO.unit)
                    )
                    .flatMap(createResources)
                    .use { eventReader =>
                        for {
                            startElement <- IO.interruptible(findRootElement(eventReader))
                            channel <- startElement match
                                case Some(el) =>
                                    given Logger[IO] = LoggerFactory[IO].getLogger
                                    val parserOpt = Parser[IO](el.getName().getLocalPart())
                                    parserOpt match
                                        case Some(parser) => parser.parse(eventReader, link)
                                        case None         => IO.pure(None)
                                case None => IO.pure(None)
                        } yield channel
                    }
            }
            .compile
            .last
            .map(_.flatten)
