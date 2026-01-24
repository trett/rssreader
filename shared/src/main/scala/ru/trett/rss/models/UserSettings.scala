package ru.trett.rss.models

final case class UserSettings(
    name: String,
    hideRead: Boolean,
    summaryLanguage: Option[String],
    aiMode: Option[Boolean] = None,
    summaryModel: Option[String] = None
):
    /** AI mode is the default. Returns true unless aiMode is explicitly set to false. */
    def isAiMode: Boolean = !aiMode.contains(false)
