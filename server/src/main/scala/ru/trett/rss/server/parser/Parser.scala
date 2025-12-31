package ru.trett.rss.server.parser

import javax.xml.stream.{XMLInputFactory, XMLEventReader}
import javax.xml.stream.events.StartElement
import cats.effect.{Async, Resource}
import cats.syntax.all._
import fs2.Stream
import org.typelevel.log4cats.Logger
import ru.trett.rss.server.models.Channel
import scala.annotation.tailrec
import java.io.InputStream

object Parser {
    private val xmlInputFactory: XMLInputFactory = {
        val factory = XMLInputFactory.newInstance()
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false)
        factory
    }

    def parse[F[_]: Async](input: Stream[F, Byte], link: String)(using
        logger: Logger[F],
        registry: FeedParserRegistry[F]
    ): F[Either[ParserError, Channel]] = {
        def findRootElement(reader: XMLEventReader): Option[StartElement] = {
            @tailrec
            def loop(): Option[StartElement] = {
                if (!reader.hasNext) None
                else {
                    reader.peek() match {
                        case start: StartElement => Some(start)
                        case _ =>
                            reader.nextEvent()
                            loop()
                    }
                }
            }

            loop()
        }

        def createReader(inputStream: InputStream): Resource[F, XMLEventReader] =
            Resource.make(Async[F].blocking(xmlInputFactory.createXMLEventReader(inputStream)))(
                reader =>
                    Async[F]
                        .blocking(reader.close())
                        .handleError(err => logger.error(err)(err.getMessage))
            )

        input
            .through(fs2.io.toInputStream)
            .evalMap { is =>
                Resource
                    .fromAutoCloseable(Async[F].blocking(is))
                    .use { inputStream =>
                        createReader(inputStream).use { reader =>
                            Async[F].blocking(findRootElement(reader)).flatMap {
                                case Some(el) =>
                                    parseChannel(link, registry, reader, el.getName.getLocalPart)
                                case None => Async[F].pure(Left(ParserError.NoRootElement))
                            }
                        }
                    }
                    .handleError(e => Left(ParserError.ParseFailure(e)))
            }
            .compile
            .last
            .map(_.getOrElse(Left(ParserError.EmptyFeed)))
    }

    private def parseChannel[F[_]: Async](
        link: String,
        registry: FeedParserRegistry[F],
        reader: XMLEventReader,
        rootName: String
    ) =
        ParserType.fromRoot(rootName) match {
            case Some(parserType) =>
                registry.get(parserType) match {
                    case Some(feedParser) =>
                        feedParser.parse(reader, link).handleError { e =>
                            Left(ParserError.ParseFailure(e))
                        }
                    case None =>
                        Async[F].pure(Left(ParserError.UnsupportedFormat(rootName)))
                }
            case None =>
                Async[F].pure(Left(ParserError.UnsupportedFormat(rootName)))
        }

}
