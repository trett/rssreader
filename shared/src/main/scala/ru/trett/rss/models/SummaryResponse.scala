package ru.trett.rss.models

sealed trait SummaryResult
final case class SummarySuccess(html: String) extends SummaryResult
final case class SummaryError(message: String) extends SummaryResult

case class SummaryResponse(
    result: SummaryResult,
    hasMore: Boolean,
    feedsProcessed: Int,
    totalRemaining: Int,
    funFact: Option[String]
)
