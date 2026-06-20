package ru.trett.rss.models

final case class UserSettings(
    name: String,
    hideRead: Boolean,
    bannedCategories: List[String] = List.empty,
    keywordRules: List[String] = List.empty,
    geminiApiKey: Option[String] = None,
    filterNews: Boolean = false
)
