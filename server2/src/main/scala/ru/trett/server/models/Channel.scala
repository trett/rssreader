package ru.trett.server.models

import doobie.util.Read
import doobie.postgres.implicits._

case class Channel(
  id: Long,
  channelLink: String,
  title: String,
  link: String,
  feedItems: List[Feed] = List.empty
)

object Channel {
  implicit val read: Read[Channel] = Read[(Long, String, String, String)].map {
    case (id, channelLink, title, link) => Channel(id, channelLink, title, link)
  }
}
