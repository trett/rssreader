package ru.trett.rss.models

enum SummaryProvider(val displayName: String):
    case Gemini extends SummaryProvider("Gemini")
    case OpenAI extends SummaryProvider("OpenAI")

object SummaryProvider:
    def fromString(s: String): Option[SummaryProvider] =
        values.find(_.displayName == s)

    def isValid(s: String): Boolean =
        fromString(s).isDefined

    def all: List[SummaryProvider] = values.toList

    def default: SummaryProvider = Gemini
