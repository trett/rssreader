package ru.trett.rss.server.controllers

import cats.effect.IO
import org.http4s.AuthedRoutes
import org.http4s.circe.CirceEntityEncoder.*
import org.http4s.dsl.io.*
import ru.trett.rss.server.models.User
import ru.trett.rss.server.services.SummarizeService
import ru.trett.rss.server.codecs.SummaryCodecs.given

object SummarizeController:

    object OffsetQueryParamMatcher extends OptionalQueryParamDecoderMatcher[Int]("offset")

    def routes(summarizeService: SummarizeService): AuthedRoutes[User, IO] =
        AuthedRoutes.of[User, IO] {
            case GET -> Root / "api" / "summarize" :? OffsetQueryParamMatcher(offset) as user =>
                for
                    summary <- summarizeService.getSummary(user, offset.getOrElse(0))
                    response <- Ok(summary)
                yield response
        }
