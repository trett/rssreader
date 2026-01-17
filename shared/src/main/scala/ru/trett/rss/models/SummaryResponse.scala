package ru.trett.rss.models

sealed trait SummaryResult
case class SummarySuccess(html: String) extends SummaryResult
case class SummaryError(message: String) extends SummaryResult

case class SummaryResponse(
    result: SummaryResult,
    hasMore: Boolean,
    feedsProcessed: Int,
    totalRemaining: Int,
    noFeeds: Boolean,
    funFact: Option[String]
)
