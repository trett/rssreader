package ru.trett.rss.server.parser

sealed trait ParserError extends Throwable:
    def message: String
    override def getMessage: String = message

object ParserError:
    final case class UnsupportedFormat(format: String) extends ParserError:
        override val message: String = s"Unsupported feed format: $format"

    final case class InvalidFeed(reason: String) extends ParserError:
        override val message: String = s"Invalid feed: $reason"

    final case class ParseFailure(cause: Throwable) extends ParserError:
        override val message: String = s"Failed to parse feed: ${cause.getMessage}"
        override def getCause: Throwable = cause

    case object NoRootElement extends ParserError:
        override val message: String = "No root element found in feed"

    case object EmptyFeed extends ParserError:
        override val message: String = "Feed appears to be empty"
