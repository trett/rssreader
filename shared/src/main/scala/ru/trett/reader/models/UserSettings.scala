package ru.trett.reader.models

final case class UserSettings(name: String, retentionDays: Int, read: Boolean)
