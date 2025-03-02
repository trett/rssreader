package ru.trett.server.models

import doobie.util.Read
import doobie.postgres.implicits._
import java.time.Instant

case class Feed(
  id: Long,
  channelId: Long,
  title: String,
  link: String,
  description: String,
  pubDate: Option[Instant] = None,
  isRead: Boolean = false
)

object Feed {
  implicit val read: Read[Feed] = Read[(Long, Long, String, String, String, Option[Instant], Boolean)].map {
    case (id, channelId, title, link, description, pubDate, isRead) => 
      Feed(id, channelId, title, link, description, pubDate, isRead)
  }
}

