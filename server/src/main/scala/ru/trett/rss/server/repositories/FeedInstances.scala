package ru.trett.rss.server.repositories

import doobie.Read
import doobie.postgres.implicits.*
import ru.trett.rss.server.models.Feed

import java.time.OffsetDateTime

private[repositories] object FeedInstances:
    given Read[Feed] = Read[
        (
            String,
            String,
            Long,
            String,
            String,
            Option[OffsetDateTime],
            Boolean,
            Option[String],
            List[String],
            Boolean
        )
    ].map {
        case (
                link,
                userId,
                channelId,
                title,
                description,
                pubDate,
                isRead,
                imageUrl,
                categories,
                important
            ) =>
            Feed(
                link,
                userId,
                channelId,
                title,
                description,
                pubDate,
                isRead,
                imageUrl,
                categories,
                important
            )
    }
