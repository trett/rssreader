package ru.trett.rss.server.models

case class User(id: String, name: String, email: String, settings: User.Settings)

object User:
    case class Settings(retentionDays: Int, read: Boolean)
