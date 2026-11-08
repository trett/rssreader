package ru.trett.rss.server.services

import cats.effect.{IO, Ref}
import cats.effect.unsafe.implicits.global
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalamock.scalatest.MockFactory
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory
import ru.trett.rss.models.{ChannelNews, FeedItemData}
import ru.trett.rss.server.models.User
import ru.trett.rss.server.repositories.{FeedRepository, UserRepository}
import ru.trett.rss.server.config.DigestConfig
import org.http4s.client.Client
import java.time.OffsetDateTime

class DigestServiceSpec extends AnyFunSuite with Matchers with MockFactory:
    given LoggerFactory[IO] = Slf4jFactory.create[IO]

    test("sends each opted-in user's full daily news only to their configured recipient") {
        val sent = Ref.of[IO, List[(String, String)]](Nil).unsafeRunSync()
        val alice = User(
            "alice",
            "Alice",
            "login@example.com",
            User.Settings(geminiApiKey = Some("alice-key"), digestEmail = Some("alice@gmail.com"))
        )
        val bob = User(
            "bob",
            "Bob",
            "other@example.com",
            User.Settings(geminiApiKey = Some("bob-key"), digestEmail = Some("bob@gmail.com"))
        )
        val users = new UserService(mock[UserRepository]):
            override def getUsers =
                IO.pure(List(alice, bob, alice.copy(id = "disabled", settings = User.Settings())))
        val feeds = new FeedService(mock[FeedRepository]):
            override def getNewsByDateGrouped(
                user: User,
                from: OffsetDateTime,
                to: OffsetDateTime,
                limit: Int,
                importantOnly: Boolean
            ) =
                importantOnly shouldBe false
                java.time.Duration.between(from, to).toHours shouldBe 24L
                limit shouldBe Int.MaxValue
                IO.pure(
                    List(
                        ChannelNews(
                            1,
                            user.id,
                            List(
                                FeedItemData(
                                    s"https://example.com/${user.id}",
                                    user.id,
                                    user.id,
                                    "news",
                                    to,
                                    true,
                                    important = false
                                )
                            )
                        )
                    )
                )
        val translator = new TranslationService(mock[Client[IO]]):
            override def translate(channel: ChannelNews, key: String) =
                key shouldBe s"${channel.channelTitle}-key"
                IO.pure(channel)
        val mailer = new DigestMailer(DigestConfig()):
            override def send(to: String, subject: String, html: String) =
                sent.update(_ :+ (to -> html))
        new DigestService(feeds, users, translator, mailer).send.unsafeRunSync()
        val messages = sent.get.unsafeRunSync()
        messages.map(_._1) shouldBe List("alice@gmail.com", "bob@gmail.com")
        messages.head._2 should include("https://example.com/alice")
        (messages.head._2 should not).include("https://example.com/bob")
        (messages(1)._2 should not).include("https://example.com/alice")
    }
