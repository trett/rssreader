package ru.trett.rss.server.controllers

import cats.effect.IO
import org.http4s.AuthedRoutes
import org.http4s.ServerSentEvent
import org.http4s.dsl.io.*
import io.circe.syntax.*
import ru.trett.rss.server.models.User
import ru.trett.rss.server.services.SummarizeService
import ru.trett.rss.server.codecs.SummaryCodecs.given

object SummarizeController:

    object OffsetQueryParamMatcher extends OptionalQueryParamDecoderMatcher[Int]("offset")

    def routes(summarizeService: SummarizeService): AuthedRoutes[User, IO] =
        AuthedRoutes.of[User, IO] {
            case GET -> Root / "api" / "summarize" :? OffsetQueryParamMatcher(offset) as user =>
                val stream = summarizeService
                    .streamSummary(user, offset.getOrElse(0))
                    .map(event => ServerSentEvent(data = Some(event.asJson.noSpaces)))
                Ok(stream)
        }
