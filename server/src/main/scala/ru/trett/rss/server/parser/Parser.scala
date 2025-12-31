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

    def parseRssEither[F[_]: Async](input: Stream[F, Byte], link: String)(using
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
                reader => Async[F].blocking(reader.close()).handleError(_ => ())
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
                                    val rootName = el.getName.getLocalPart
                                    ParserType.fromRoot(rootName) match {
                                        case Some(parserType) =>
                                            registry.get(parserType) match {
                                                case Some(parser) =>
                                                    parser.parse(reader, link).handleError { e =>
                                                        Left(ParserError.ParseFailure(e))
                                                    }
                                                case None =>
                                                    Async[F].pure(
                                                        Left(
                                                            ParserError.UnsupportedFormat(rootName)
                                                        )
                                                    )
                                            }
                                        case None =>
                                            Async[F].pure(
                                                Left(ParserError.UnsupportedFormat(rootName))
                                            )
                                    }
                                case None =>
                                    Async[F].pure(Left(ParserError.NoRootElement))
                            }
                        }
                    }
            }
            .compile
            .last
            .map(_.getOrElse(Left(ParserError.EmptyFeed)))
    }

    def parseRss[F[_]: Async](input: Stream[F, Byte], link: String)(using
        logger: Logger[F],
        registry: FeedParserRegistry[F]
    ): F[Option[Channel]] =
        parseRssEither(input, link).map(_.toOption)
}
