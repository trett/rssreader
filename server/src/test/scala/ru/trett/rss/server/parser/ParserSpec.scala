package ru.trett.rss.server.parser

import com.rometools.rome.feed.synd.SyndEntry
import com.rometools.rome.io.SyndFeedInput
import com.rometools.rome.io.XmlReader
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import ru.trett.rss.server.models.Channel
import ru.trett.rss.server.models.Feed
import ru.trett.rss.server.parser.Parser

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import scala.jdk.CollectionConverters.*
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import fs2.io.readInputStream
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.noop.NoOpFactory

class ParserSpec extends AnyFunSuite with Matchers {

    private val dateFormatter: DateTimeFormatter = DateTimeFormatter.RFC_1123_DATE_TIME

    given LoggerFactory[IO] = NoOpFactory[IO]

    private def streamFromInputStream(is: java.io.InputStream): fs2.Stream[IO, String] =
        readInputStream(IO(is), 4096)
            .through(fs2.text.utf8.decode)

    test("Parser should parse RSS 2.0 feed first version") {
        val inputStream = Option(getClass.getResourceAsStream("/rss_2_0_1.xml"))
            .getOrElse(fail("Resource file rss_2_0_1.xml should exist"))

        val result: Option[Channel] = Parser
            .parseRss(streamFromInputStream(inputStream), "https://www.linux.org.ru/")
            .unsafeRunSync()

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

    test("Parser should parse RSS 2.0 feed second version") {
        val inputStream = Option(getClass.getResourceAsStream("/rss_2_0_2.xml"))
            .getOrElse(fail("Resource file rss_2_0_2.xml should exist"))

        val result: Option[Channel] = Parser
            .parseRss(streamFromInputStream(inputStream), "https://github.blog/changelog/")
            .unsafeRunSync()

        result shouldBe defined

        val channel = result.get

        channel.title shouldBe "Archive: 2025 - GitHub Changelog"
        channel.link shouldBe "https://github.blog/changelog/"
        channel.feedItems should not be empty
        channel.feedItems.length shouldBe 10

        val firstItem = channel.feedItems.head
        firstItem.title shouldBe "Track Copilot code generation metrics in a dashboard"
        firstItem.link shouldBe "https://github.blog/changelog/2025-12-05-track-copilot-code-generation-metrics-in-a-dashboard"
        firstItem.description should include(
            "You can now view GitHub Copilot lines of code (LoC) metrics"
        )

        val expectedDate = OffsetDateTime.parse("Fri, 05 Dec 2025 18:40:33 +0000", dateFormatter)
        firstItem.pubDate shouldBe Some(expectedDate)

        val secondItem = channel.feedItems(1)
        secondItem.title shouldBe "Actions workflow dispatch workflows now support 25 inputs"
        secondItem.link shouldBe "https://github.blog/changelog/2025-12-04-actions-workflow-dispatch-workflows-now-support-25-inputs"

        val lastItem = channel.feedItems.last
        lastItem.title shouldBe "Secret scanning updates — November 2025"
        lastItem.link shouldBe "https://github.blog/changelog/2025-12-02-secret-scanning-updates-november-2025"
    }

    test("Parser should parse Atom 1.0 feed first version") {
        val inputStream = Option(getClass.getResourceAsStream("/atom_1_0_1.xml"))
            .getOrElse(fail("Resource file atom_1_0_1.xml should exist"))

        val result: Option[Channel] =
            Parser
                .parseRss(streamFromInputStream(inputStream), "https://v3spec.msn.com/myfeed.xml")
                .unsafeRunSync()

        result shouldBe defined

        val channel = result.get

        channel.title shouldBe "Feed title"
        channel.link shouldBe "https://v3spec.msn.com/myfeed.xml"
        channel.feedItems should not be empty
        channel.feedItems.length shouldBe 2

        val expectedUpdatedDate =
            OffsetDateTime.ofInstant(Instant.parse("2017-10-02T13:00:00.520Z"), ZoneId.of("UTC"))
        val firstItem = channel.feedItems.head
        firstItem.title shouldBe "High-definition video encoding and streaming"
        firstItem.link shouldBe "https://sample-feeds.rowanmanning.com/examples/222780a7caac12b938dfe09cd7d138f9/feed.xml"
        firstItem.description should include(
            "Media Services enables you to encode your media files"
        )
        firstItem.pubDate shouldBe Some(expectedUpdatedDate)
    }

    test("Parser should parse Atom 1.0 feed second version") {
        val inputStream = Option(getClass.getResourceAsStream("/atom_1_0_2.xml"))
            .getOrElse(fail("Resource file atom_1_0_2.xml should exist"))

        val result: Option[Channel] = Parser
            .parseRss(streamFromInputStream(inputStream), "http://example.org/")
            .unsafeRunSync()

        result shouldBe defined

        val channel = result.get

        // Verify basic feed metadata
        channel.title shouldBe "Example Feed"
        channel.link shouldBe "http://example.org/"
        channel.feedItems should not be empty
        channel.feedItems.length shouldBe 3

        val expectedDate =
            OffsetDateTime.ofInstant(Instant.parse("2025-12-07T18:30:02Z"), ZoneId.of("UTC"))
        // Verify first entry
        val firstItem = channel.feedItems.head
        firstItem.title shouldBe "Atom-Powered Robots Run Amok"
        firstItem.link shouldBe "http://example.org/2025/12/07/atom"
        firstItem.description shouldBe "Detailed content about Atom-powered robots running amok in the city."
        firstItem.pubDate shouldBe Some(expectedDate)

        // Verify second entry
        val secondItem = channel.feedItems(1)
        secondItem.title shouldBe "Second Entry Example"
        secondItem.link shouldBe "http://example.org/2025/12/06/second"
        secondItem.description shouldBe "Summary of the second entry"
        secondItem.pubDate shouldBe defined

        // Verify third entry
        val thirdItem = channel.feedItems(2)
        thirdItem.title shouldBe "Third Entry with Only Updated Date"
        thirdItem.link shouldBe "http://example.org/2025/12/05/third"
        thirdItem.description shouldBe "This entry has HTML content instead of summary."
        thirdItem.pubDate shouldBe defined
    }

    test("Parser should parse Atom 1.0 feed third version)") {
        val inputStream = Option(getClass.getResourceAsStream("/atom_1_0_3.xml"))
            .getOrElse(fail("Resource file atom_1_0_3.xml should exist"))

        val result: Option[Channel] =
            Parser
                .parseRss(streamFromInputStream(inputStream), "https://www.reddit.com/r/java/.rss")
                .unsafeRunSync()

        result shouldBe defined

        val channel = result.get

        channel.title shouldBe "Java News/Tech/Discussion/etc. No programming help, no learning Java"
        channel.link shouldBe "https://www.reddit.com/r/java/.rss"
        channel.feedItems should not be empty
        channel.feedItems.length shouldBe 25

        val firstItem = channel.feedItems.head
        firstItem.title should include("[PSA]/r/java is not for programming help")
        firstItem.link shouldBe "https://www.reddit.com/r/java/comments/j7h9er/psarjava_is_not_for_programming_help_learning/"
        firstItem.description should include("Learning related questions")

        val expectedDate =
            OffsetDateTime.ofInstant(Instant.parse("2020-10-08T17:21:51+00:00"), ZoneId.of("UTC"))
        firstItem.pubDate shouldBe Some(expectedDate)

        val secondItem = channel.feedItems(1)
        secondItem.title should include("Pure Java LLaMA Transformers Compilied")
        secondItem.link shouldBe "https://www.reddit.com/r/java/comments/1pjwsxt/gpullama3java_release_v030_pure_java_llama/"
        secondItem.description should include("Source the project-specific environment paths")

        val lastItem = channel.feedItems.last
        lastItem.title shouldBe "Jetbrains IDE Debugger MCP Server - Let Claude autonomously use IntelliJ debugger"
        lastItem.link shouldBe "https://www.reddit.com/r/java/comments/1peoil3/jetbrains_ide_debugger_mcp_server_let_claude/"
        lastItem.description should include("JetBrains")
    }

    test("Parser should return None for invalid XML") {
        val invalidXml = "not an xml".getBytes()
        val inputStream = new java.io.ByteArrayInputStream(invalidXml)

        val result =
            try {
                Parser
                    .parseRss(streamFromInputStream(inputStream), "http://example.com/")
                    .unsafeRunSync()
            } catch {
                case _: Exception => None
            }

        result shouldBe None
    }

    private def parseWithSyndFeed(resourcePath: String, link: String): Option[Channel] = {
        val inputStream = Option(getClass.getResourceAsStream(resourcePath))
            .getOrElse(fail(s"Resource file $resourcePath should exist"))

        try {
            val reader = new XmlReader(inputStream)
            val input = new SyndFeedInput()
            val syndFeed = input.build(reader)

            val title = syndFeed.getTitle
            val zoneId = java.time.ZoneId.systemDefault()

            val feedItems = syndFeed.getEntries.asScala.map { entry =>
                Feed(
                    link = entry.getLink,
                    userId = "",
                    channelId = 0L,
                    title = entry.getTitle,
                    description = extractDescription(entry),
                    pubDate = extractDate(entry, zoneId)
                )
            }.toList

            Some(Channel(id = 0L, title = title, link = link, feedItems = feedItems))
        } catch {
            case _: Exception => None
        }
    }

    private def extractDescription(entry: SyndEntry): String =
        Option(entry.getDescription)
            .orElse {
                Option(entry.getContents)
                    .filter(_.size > 0)
                    .map(_.get(0))
            }
            .map(_.getValue)
            .getOrElse("")

    private def extractDate(entry: SyndEntry, zoneId: ZoneId): Option[OffsetDateTime] =
        Option(entry.getPublishedDate)
            .orElse(Option(entry.getUpdatedDate))
            .map(t => OffsetDateTime.ofInstant(t.toInstant, zoneId))

    test("Compare custom Parser with SyndFeedInput for RSS 2.0 feed first version") {
        val link = "https://www.linux.org.ru/"

        val customResult = {
            val inputStream = Option(getClass.getResourceAsStream("/rss_2_0_1.xml"))
                .getOrElse(fail("Resource file rss_2_0_1.xml should exist"))
            Parser.parseRss(streamFromInputStream(inputStream), link).unsafeRunSync()
        }

        val syndResult = parseWithSyndFeed("/rss_2_0_1.xml", link)

        customResult shouldBe defined
        syndResult shouldBe defined

        val customChannel = customResult.get
        val syndChannel = syndResult.get

        println(s"\n=== RSS 2.0 Feed 1 Comparison ===")
        println(s"Custom Parser:")
        println(s"  Title: ${customChannel.title}")
        println(s"  Link: ${customChannel.link}")
        println(s"  Item count: ${customChannel.feedItems.length}")
        println(s"  First item title: ${customChannel.feedItems.head.title}")
        println(s"  First item link: ${customChannel.feedItems.head.link}")
        println(s"  First item date: ${customChannel.feedItems.head.pubDate}")

        println(s"\nSyndFeedInput:")
        println(s"  Title: ${syndChannel.title}")
        println(s"  Link: ${syndChannel.link}")
        println(s"  Item count: ${syndChannel.feedItems.length}")
        println(s"  First item title: ${syndChannel.feedItems.head.title}")
        println(s"  First item link: ${syndChannel.feedItems.head.link}")
        println(s"  First item date: ${syndChannel.feedItems.head.pubDate}")

        println(s"\nMatches:")
        println(s"  Title: ${customChannel.title == syndChannel.title}")
        println(s"  Link: ${customChannel.link == syndChannel.link}")
        println(s"  Item count: ${customChannel.feedItems.length == syndChannel.feedItems.length}")
        println(
            s"  First item title: ${customChannel.feedItems.head.title == syndChannel.feedItems.head.title}"
        )
        println(
            s"  First item link: ${customChannel.feedItems.head.link == syndChannel.feedItems.head.link}"
        )

        val dateMatch =
            (customChannel.feedItems.head.pubDate, syndChannel.feedItems.head.pubDate) match {
                case (Some(d1), Some(d2)) => d1.toInstant == d2.toInstant
                case (None, None)         => true
                case _                    => false
            }
        println(s"  First item date (instant): $dateMatch")

        // Non-strict assertions
        customChannel.title shouldBe syndChannel.title
        customChannel.feedItems.length shouldBe syndChannel.feedItems.length
        customChannel.feedItems.head.title shouldBe syndChannel.feedItems.head.title
        customChannel.feedItems.head.link shouldBe syndChannel.feedItems.head.link
    }

    test("Compare custom Parser with SyndFeedInput for RSS 2.0 feed second version") {
        val link = "https://github.blog/changelog/"

        val customResult = {
            val inputStream = Option(getClass.getResourceAsStream("/rss_2_0_2.xml"))
                .getOrElse(fail("Resource file rss_2_0_2.xml should exist"))
            Parser.parseRss(streamFromInputStream(inputStream), link).unsafeRunSync()
        }

        val syndResult = parseWithSyndFeed("/rss_2_0_2.xml", link)

        customResult shouldBe defined
        syndResult shouldBe defined

        val customChannel = customResult.get
        val syndChannel = syndResult.get

        println(s"\n=== RSS 2.0 Feed 2 Comparison ===")
        println(s"Custom Parser:")
        println(s"  Title: ${customChannel.title}")
        println(s"  Item count: ${customChannel.feedItems.length}")
        println(s"  First item: ${customChannel.feedItems.head.title}")
        println(s"  First item date: ${customChannel.feedItems.head.pubDate}")

        println(s"\nSyndFeedInput:")
        println(s"  Title: ${syndChannel.title}")
        println(s"  Item count: ${syndChannel.feedItems.length}")
        println(s"  First item: ${syndChannel.feedItems.head.title}")
        println(s"  First item date: ${syndChannel.feedItems.head.pubDate}")

        customChannel.title shouldBe syndChannel.title
        customChannel.feedItems.length shouldBe syndChannel.feedItems.length
        customChannel.feedItems.head.title shouldBe syndChannel.feedItems.head.title
    }

    test("Compare custom Parser with SyndFeedInput for Atom 1.0 feed first version") {
        val link = "https://v3spec.msn.com/myfeed.xml"

        val customResult = {
            val inputStream = Option(getClass.getResourceAsStream("/atom_1_0_1.xml"))
                .getOrElse(fail("Resource file atom_1_0_1.xml should exist"))
            Parser.parseRss(streamFromInputStream(inputStream), link).unsafeRunSync()
        }

        val syndResult = parseWithSyndFeed("/atom_1_0_1.xml", link)

        customResult shouldBe defined
        syndResult shouldBe defined

        val customChannel = customResult.get
        val syndChannel = syndResult.get

        println(s"\n=== Atom 1.0 Feed 1 Comparison ===")
        println(s"Custom Parser:")
        println(s"  Title: ${customChannel.title}")
        println(s"  Item count: ${customChannel.feedItems.length}")
        println(s"  First item title: ${customChannel.feedItems.head.title}")
        println(s"  First item link: ${customChannel.feedItems.head.link}")

        println(s"\nSyndFeedInput:")
        println(s"  Title: ${syndChannel.title}")
        println(s"  Item count: ${syndChannel.feedItems.length}")
        println(s"  First item title: ${syndChannel.feedItems.head.title}")
        println(s"  First item link: ${syndChannel.feedItems.head.link}")

        println(s"\nLink Extraction Difference:")
        println(s"  In atom_1_0_1.xml, the entry has: <link rel=\"self\" href=\"...\"/>")
        println(
            s"  Custom parser: extracts 'self' link (accepts 'alternate', 'self', or empty rel)"
        )
        println(
            s"  SyndFeedInput: entry.getLink() returns null (ignores 'self' rel, expects 'alternate')"
        )
        println(s"  This feed doesn't have a proper alternate link to an article page")

        customChannel.title shouldBe syndChannel.title
        customChannel.feedItems.length shouldBe syndChannel.feedItems.length
        customChannel.feedItems.head.title shouldBe syndChannel.feedItems.head.title
    }

    test("Compare custom Parser with SyndFeedInput for Atom 1.0 feed second version") {
        val link = "http://example.org/"

        val customResult = {
            val inputStream = Option(getClass.getResourceAsStream("/atom_1_0_2.xml"))
                .getOrElse(fail("Resource file atom_1_0_2.xml should exist"))
            Parser.parseRss(streamFromInputStream(inputStream), link).unsafeRunSync()
        }

        val syndResult = parseWithSyndFeed("/atom_1_0_2.xml", link)

        customResult shouldBe defined
        syndResult shouldBe defined

        val customChannel = customResult.get
        val syndChannel = syndResult.get

        println(s"\n=== Atom 1.0 Feed 2 Comparison ===")
        println(s"Custom Parser:")
        println(s"  Title: ${customChannel.title}")
        println(s"  Item count: ${customChannel.feedItems.length}")
        println(s"  First item: ${customChannel.feedItems.head.title}")
        println(s"  First item date: ${customChannel.feedItems.head.pubDate}")

        println(s"\nSyndFeedInput:")
        println(s"  Title: ${syndChannel.title}")
        println(s"  Item count: ${syndChannel.feedItems.length}")
        println(s"  First item: ${syndChannel.feedItems.head.title}")
        println(s"  First item date: ${syndChannel.feedItems.head.pubDate}")

        println(
            s"\nNote: Dates may differ in timezone representation but represent the same instant"
        )

        customChannel.title shouldBe syndChannel.title
        customChannel.feedItems.length shouldBe syndChannel.feedItems.length
        customChannel.feedItems.head.title shouldBe syndChannel.feedItems.head.title
    }

    test("Compare custom Parser with SyndFeedInput for Atom 1.0 feed third version") {
        val link = "https://www.reddit.com/r/java/.rss"

        val customResult = {
            val inputStream = Option(getClass.getResourceAsStream("/atom_1_0_3.xml"))
                .getOrElse(fail("Resource file atom_1_0_3.xml should exist"))
            Parser.parseRss(streamFromInputStream(inputStream), link).unsafeRunSync()
        }

        val syndResult = parseWithSyndFeed("/atom_1_0_3.xml", link)

        customResult shouldBe defined
        syndResult shouldBe defined

        val customChannel = customResult.get
        val syndChannel = syndResult.get

        println(s"\n=== Atom 1.0 Feed 3 Comparison ===")
        println(s"Custom Parser:")
        println(s"  Title: ${customChannel.title}")
        println(s"  Item count: ${customChannel.feedItems.length}")
        println(s"  First item: ${customChannel.feedItems.head.title}")
        println(s"  First item date: ${customChannel.feedItems.head.pubDate}")

        println(s"\nSyndFeedInput:")
        println(s"  Title: ${syndChannel.title}")
        println(s"  Item count: ${syndChannel.feedItems.length}")
        println(s"  First item: ${syndChannel.feedItems.head.title}")
        println(s"  First item date: ${syndChannel.feedItems.head.pubDate}")

        customChannel.title shouldBe syndChannel.title
        customChannel.feedItems.length shouldBe syndChannel.feedItems.length
        customChannel.feedItems.head.title shouldBe syndChannel.feedItems.head.title
    }
}
