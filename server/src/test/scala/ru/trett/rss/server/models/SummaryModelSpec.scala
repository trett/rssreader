package ru.trett.rss.server.models

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import ru.trett.rss.models.{SummaryModel, SummaryProvider}

class SummaryModelSpec extends AnyFunSuite with Matchers:

    test("allFor should return only models for the selected provider") {
        SummaryModel.allFor(SummaryProvider.Gemini).foreach { model =>
            model.provider shouldBe SummaryProvider.Gemini
        }

        SummaryModel.allFor(SummaryProvider.OpenAI).foreach { model =>
            model.provider shouldBe SummaryProvider.OpenAI
        }
    }

    test("fromString should validate models within the selected provider") {
        SummaryModel.fromString(SummaryProvider.OpenAI, "GPT-5 mini") shouldBe Some(
            SummaryModel.Gpt5Mini
        )
        SummaryModel.fromString(SummaryProvider.OpenAI, "Gemini 2.5 Flash") shouldBe None
    }

    test("defaultFor should use provider-specific defaults") {
        SummaryModel.defaultFor(SummaryProvider.Gemini) shouldBe SummaryModel.Gemini3FlashPreview
        SummaryModel.defaultFor(SummaryProvider.OpenAI) shouldBe SummaryModel.Gpt5Mini
    }
