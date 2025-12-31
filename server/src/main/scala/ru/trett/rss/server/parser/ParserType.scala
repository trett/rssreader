package ru.trett.rss.server.parser

sealed trait ParserType {
    def rootElement: String
}

object ParserType {
    case object Rss20 extends ParserType {
        val rootElement: String = "rss"
    }

    case object Atom10 extends ParserType {
        val rootElement: String = "feed"
    }

    val values: List[ParserType] = List(Rss20, Atom10)

    def fromRoot(root: String): Option[ParserType] =
        values.find(_.rootElement.equalsIgnoreCase(root))
}
