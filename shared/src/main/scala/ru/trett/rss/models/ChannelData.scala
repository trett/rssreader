package ru.trett.rss.models

final case class ChannelData(id: Long, title: String, link: String, highlighted: Boolean = false)
