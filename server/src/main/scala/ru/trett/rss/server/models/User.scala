package ru.trett.rss.server.models

import io.circe.Encoder
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder

case class User(id: String, name: String, email: String, settings: User.Settings)

object User:
    given Encoder[User] = deriveEncoder
    given Decoder[User] = deriveDecoder
    given Decoder[User.Settings] = deriveDecoder
    given Encoder[User.Settings] = deriveEncoder

    case class Settings(
        hideRead: Boolean = false,
        summaryLanguage: Option[String] = None,
        aiMode: Option[Boolean] = None,
        summaryModel: Option[String] = None
    ):
        /** AI mode is the default. Returns true unless aiMode is explicitly set to false. */
        def isAiMode: Boolean = !aiMode.contains(false)
