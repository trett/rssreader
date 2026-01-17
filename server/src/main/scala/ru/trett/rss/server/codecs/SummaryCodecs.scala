package ru.trett.rss.server.codecs

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import io.circe.syntax.*
import ru.trett.rss.models.{SummaryResult, SummarySuccess, SummaryError, SummaryResponse}

object SummaryCodecs:
    given Encoder[SummarySuccess] = deriveEncoder
    given Encoder[SummaryError] = deriveEncoder

    given Encoder[SummaryResult] = Encoder.instance {
        case s @ SummarySuccess(_) =>
            s.asJson.mapObject(_.add("type", "success".asJson))
        case e @ SummaryError(_) =>
            e.asJson.mapObject(_.add("type", "error".asJson))
    }

    given Decoder[SummarySuccess] = deriveDecoder
    given Decoder[SummaryError] = deriveDecoder

    given Decoder[SummaryResult] =
        Decoder.instance { cursor =>
            cursor.downField("type").as[String].flatMap {
                case "success" => cursor.as[SummarySuccess]
                case "error" => cursor.as[SummaryError]
                case other => Left(io.circe.DecodingFailure(s"Unknown SummaryResult type: $other", cursor.history))
            }
        }

    given Encoder[SummaryResponse] = deriveEncoder
    given Decoder[SummaryResponse] = deriveDecoder
