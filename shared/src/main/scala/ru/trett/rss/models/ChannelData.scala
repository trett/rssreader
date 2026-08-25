package ru.trett.rss.models

final case class ChannelData(
    id: Long,
    title: String,
    link: String,
    highlighted: Boolean = false,
    /** Sidebar group. `None` means the channel is not filed under any folder. */
    folder: Option[String] = None
)
