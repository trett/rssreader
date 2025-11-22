package ru.trett.rss.models

enum SummaryLanguage(val displayName: String):
    case English extends SummaryLanguage("English")
    case Serbian extends SummaryLanguage("Serbian")
    case Russian extends SummaryLanguage("Russian")
    case German extends SummaryLanguage("German")
    case Spanish extends SummaryLanguage("Spanish")

object SummaryLanguage:
    def fromString(s: String): Option[SummaryLanguage] =
        values.find(_.displayName == s)

    def isValid(s: String): Boolean =
        fromString(s).isDefined

    def all: List[SummaryLanguage] = values.toList
