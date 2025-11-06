package ru.trett.rss.models

final case class UserSettings(name: String, hideRead: Boolean, summaryLanguage: Option[String])
