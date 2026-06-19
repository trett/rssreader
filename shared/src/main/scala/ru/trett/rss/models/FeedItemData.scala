package ru.trett.rss.models

import java.time.OffsetDateTime

case class FeedItemData(
    link: String,
    channelTitle: String,
    title: String,
    description: String,
    pubDate: OffsetDateTime,
    isRead: Boolean,
    highlighted: Boolean = false,
    imageUrl: Option[String] = None,
    important: Boolean = false
)
