package ru.trett.rss.server.models

import java.time.OffsetDateTime

case class Feed(
    link: String,
    userId: String,
    channelId: Long,
    title: String,
    description: String,
    pubDate: Option[OffsetDateTime] = None,
    isRead: Boolean = false,
    imageUrl: Option[String] = None,
    categories: List[String] = List.empty,
    important: Boolean = false
)
