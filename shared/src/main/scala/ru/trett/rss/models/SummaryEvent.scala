package ru.trett.rss.models

sealed trait SummaryEvent

object SummaryEvent {
    case class Content(text: String) extends SummaryEvent
    case class Metadata(feedsProcessed: Int, totalRemaining: Int, hasMore: Boolean)
        extends SummaryEvent
    case class FunFact(text: String) extends SummaryEvent
    case class Error(message: String) extends SummaryEvent
    case object Done extends SummaryEvent
}
