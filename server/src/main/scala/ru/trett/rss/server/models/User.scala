package ru.trett.rss.server.models

import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder

case class User(id: String, name: String, email: String, settings: User.Settings)

object User:
    given Encoder[User] = deriveEncoder
    given Decoder[User] = deriveDecoder
    given Encoder[User.Settings] = deriveEncoder

    given Decoder[User.Settings] = Decoder.instance { c =>
        for
            hideRead <- c.getOrElse[Boolean]("hideRead")(false)
            bannedCategories <- c.getOrElse[List[String]]("bannedCategories")(List.empty)
            keywordRules <- c.getOrElse[List[String]]("keywordRules")(List.empty)
            geminiApiKey <- c.getOrElse[Option[String]]("geminiApiKey")(None)
            filterNews <- c.getOrElse[Boolean]("filterNews")(false)
        yield Settings(hideRead, bannedCategories, keywordRules, geminiApiKey, filterNews)
    }

    case class Settings(
        hideRead: Boolean = false,
        bannedCategories: List[String] = List.empty,
        keywordRules: List[String] = List.empty,
        geminiApiKey: Option[String] = None,
        filterNews: Boolean = false
    ):
        override def toString: String =
            s"Settings(hideRead=$hideRead, bannedCategories=$bannedCategories, keywordRules=$keywordRules, geminiApiKey=${geminiApiKey
                    .map(_ => "<redacted>")}, filterNews=$filterNews)"
