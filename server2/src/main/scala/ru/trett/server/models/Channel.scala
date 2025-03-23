package ru.trett.server.models

import doobie.util.Read
import doobie.postgres.implicits._

case class Channel(
  id: Long,
  title: String,
  link: String,
  feedItems: List[Feed] = List.empty
)

object Channel {
  implicit val read: Read[Channel] = Read[(Long, String, String)].map {
    case (id, title, link) => Channel(id, title, link)
  }
}
