package ru.trett.rss.server.parser

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import ru.trett.rss.server.models.Channel
import ru.trett.server.rss.parser.Parser

import java.io.InputStream
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

class ParserSpec extends AnyFunSuite with Matchers {

    private val dateFormatter: DateTimeFormatter = DateTimeFormatter.RFC_1123_DATE_TIME

    test("Parser should parse RSS 2.0 feed") {
        val inputStream: InputStream = getClass.getResourceAsStream("/rss_2_0.xml")
        assert(inputStream != null, "Resource file rss_2_0.xml should exist")

        val result: Option[Channel] = Parser.parseRss(inputStream)

        result shouldBe defined

        val channel = result.get

        channel.title shouldBe "Linux.org.ru: Новости"
        channel.link shouldBe "https://www.linux.org.ru/"
        channel.feedItems should not be empty
        channel.feedItems.length shouldBe 30

        val firstItem = channel.feedItems.head
        firstItem.title shouldBe "Jolla открыла предзаказ на нативно-линуксовый смартфон 5 поколения"
        firstItem.link shouldBe "https://www.linux.org.ru/news/pda/18161013"
        firstItem.description should include("Компания Jolla открыла предзаказ")

        val expectedDate = OffsetDateTime.parse("Sat, 6 Dec 2025 23:53:55 +0300", dateFormatter)
        firstItem.pubDate shouldBe Some(expectedDate)

        val secondItem = channel.feedItems(1)
        secondItem.title shouldBe "STERWA-erx - Simple TERminal WAllet 0.1"
        secondItem.link shouldBe "https://www.linux.org.ru/news/opensource/18161017"

        val lastItem = channel.feedItems.last
        lastItem.title shouldBe "Google ищет способ оставить возможность установки неверифицированных приложений в Android"
        lastItem.link shouldBe "https://www.linux.org.ru/news/android/18141771"
    }

    test("Parser should parse RSS 2.0.1 feed") {
        val inputStream: InputStream = getClass.getResourceAsStream("/rss2_0_1.xml")
        assert(inputStream != null, "Resource file rss2_0_1.xml should exist")

        val result: Option[Channel] = Parser.parseRss(inputStream)

        result shouldBe defined

        val channel = result.get

        channel.title shouldBe "Archive: 2025 - GitHub Changelog"
        channel.link shouldBe "https://github.blog/changelog/"
        channel.feedItems should not be empty
        channel.feedItems.length shouldBe 10

        val firstItem = channel.feedItems.head
        firstItem.title shouldBe "Track Copilot code generation metrics in a dashboard"
        firstItem.link shouldBe "https://github.blog/changelog/2025-12-05-track-copilot-code-generation-metrics-in-a-dashboard"
        firstItem.description should include("You can now view GitHub Copilot lines of code (LoC) metrics")

        val expectedDate = OffsetDateTime.parse("Fri, 05 Dec 2025 18:40:33 +0000", dateFormatter)
        firstItem.pubDate shouldBe Some(expectedDate)

        val secondItem = channel.feedItems(1)
        secondItem.title shouldBe "Actions workflow dispatch workflows now support 25 inputs"
        secondItem.link shouldBe "https://github.blog/changelog/2025-12-04-actions-workflow-dispatch-workflows-now-support-25-inputs"

        val lastItem = channel.feedItems.last
        lastItem.title shouldBe "Secret scanning updates — November 2025"
        lastItem.link shouldBe "https://github.blog/changelog/2025-12-02-secret-scanning-updates-november-2025"
    }

    test("Parser should return None for invalid XML") {
        val invalidXml = "not an xml".getBytes()
        val inputStream = new java.io.ByteArrayInputStream(invalidXml)

        val result =
            try {
                Parser.parseRss(inputStream)
            } catch {
                case _: Exception => None
            }

        result shouldBe None
    }
}
