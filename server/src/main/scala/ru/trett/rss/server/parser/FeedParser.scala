package ru.trett.rss.server.parser

import javax.xml.stream.XMLEventReader
import cats.effect.Sync
import org.typelevel.log4cats.Logger
import ru.trett.rss.server.models.Channel

trait FeedParser[F[_], -A] {
    private[parser] def parse(reader: A, link: String): F[Either[ParserError, Channel]]
}

trait FeedParserRegistry[F[_]]:
    def get(parserType: ParserType): Option[FeedParser[F, XMLEventReader]]

object FeedParserRegistry:
    given default[F[_]](using Sync[F], Logger[F]): FeedParserRegistry[F] with
        override def get(parserType: ParserType): Option[FeedParser[F, XMLEventReader]] =
            parserType match
                case ParserType.Rss20  => Some(Rss_2_0_Parser.make[F])
                case ParserType.Atom10 => Some(Atom_1_0_Parser.make[F])
