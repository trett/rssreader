package ru.trett.reader.models

import java.time.OffsetDateTime

case class FeedItemData(
    link: String,
    channelTitle: String,
    title: String,
    description: String,
    pubDate: OffsetDateTime,
    isRead: Boolean
)
