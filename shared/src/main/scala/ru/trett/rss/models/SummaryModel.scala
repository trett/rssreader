package ru.trett.rss.models

enum SummaryModel(val displayName: String, val modelId: String, val provider: SummaryProvider):
    case Gemini3FlashPreview
        extends SummaryModel(
            "Gemini 3 Flash Preview",
            "gemini-3-flash-preview",
            SummaryProvider.Gemini
        )
    case GeminiFlashLatest
        extends SummaryModel("Gemini Flash Latest", "gemini-flash-latest", SummaryProvider.Gemini)
    case Gemini3ProPreview
        extends SummaryModel("Gemini 3 Pro Preview", "gemini-3-pro-preview", SummaryProvider.Gemini)
    case Gemini25Pro
        extends SummaryModel("Gemini 2.5 Pro", "gemini-2.5-pro", SummaryProvider.Gemini)
    case Gemini25Flash
        extends SummaryModel("Gemini 2.5 Flash", "gemini-2.5-flash", SummaryProvider.Gemini)
    case Gpt5Mini extends SummaryModel("GPT-5 mini", "gpt-5-mini", SummaryProvider.OpenAI)
    case Gpt41Mini extends SummaryModel("GPT-4.1 mini", "gpt-4.1-mini", SummaryProvider.OpenAI)
    case Gpt41 extends SummaryModel("GPT-4.1", "gpt-4.1", SummaryProvider.OpenAI)

object SummaryModel:
    def fromString(s: String): Option[SummaryModel] =
        values.find(_.displayName == s)

    def fromString(provider: SummaryProvider, s: String): Option[SummaryModel] =
        allFor(provider).find(_.displayName == s)

    def fromModelId(s: String): Option[SummaryModel] =
        values.find(_.modelId == s)

    def isValid(s: String): Boolean =
        fromString(s).isDefined

    def all: List[SummaryModel] = values.toList

    def allFor(provider: SummaryProvider): List[SummaryModel] =
        values.filter(_.provider == provider).toList

    def default: SummaryModel = defaultFor(SummaryProvider.default)

    def defaultFor(provider: SummaryProvider): SummaryModel = provider match
        case SummaryProvider.Gemini => Gemini3FlashPreview
        case SummaryProvider.OpenAI => Gpt5Mini

    /** Determines if a Gemini model uses thinkingLevel configuration (Gemini 3.x models) */
    def usesThinkingLevel(modelId: String): Boolean =
        modelId.contains("gemini-3")

    /** Determines if a Gemini model uses thinkingBudget configuration (Gemini 2.5 models and
      * flash-latest)
      */
    def usesThinkingBudget(modelId: String): Boolean =
        modelId.contains("2.5") || modelId.contains("flash-latest")
