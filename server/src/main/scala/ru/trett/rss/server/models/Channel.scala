package ru.trett.rss.server.models

case class Channel(id: Long, title: String, link: String, feedItems: List[Feed] = List.empty)
