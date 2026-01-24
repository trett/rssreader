package ru.trett.rss.models

enum SummaryModel(val displayName: String, val modelId: String):
    case Gemini3FlashPreview
        extends SummaryModel("Gemini 3 Flash Preview", "gemini-3-flash-preview")
    case GeminiFlashLatest extends SummaryModel("Gemini Flash Latest", "gemini-flash-latest")
    case Gemini3ProPreview extends SummaryModel("Gemini 3 Pro Preview", "gemini-3-pro-preview")
    case Gemini25Pro extends SummaryModel("Gemini 2.5 Pro", "gemini-2.5-pro")
    case Gemini25Flash extends SummaryModel("Gemini 2.5 Flash", "gemini-2.5-flash")

object SummaryModel:
    def fromString(s: String): Option[SummaryModel] =
        values.find(_.displayName == s)

    def fromModelId(s: String): Option[SummaryModel] =
        values.find(_.modelId == s)

    def isValid(s: String): Boolean =
        fromString(s).isDefined

    def all: List[SummaryModel] = values.toList

    def default: SummaryModel = Gemini3FlashPreview
