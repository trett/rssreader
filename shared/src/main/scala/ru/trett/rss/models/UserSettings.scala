package ru.trett.rss.models

final case class UserSettings(name: String, retentionDays: Int, hideRead: Boolean)
