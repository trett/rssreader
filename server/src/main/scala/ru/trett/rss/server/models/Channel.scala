package ru.trett.rss.server.models

case class Channel(id: Long, title: String, link: String, feedItems: List[Feed] = List.empty)

/** A channel as it belongs to one user: the shared [[Channel]] plus that user's own settings for it
  * from `user_channels`. Kept as a named type rather than a tuple because the per-user columns keep
  * growing.
  */
case class UserChannel(channel: Channel, highlighted: Boolean, folder: Option[String])
