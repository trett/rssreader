package ru.trett.rss.server.controllers

import cats.effect.IO
import org.http4s.AuthedRoutes
import org.http4s.dsl.io._
import ru.trett.rss.server.models.User
import ru.trett.rss.server.services.SummarizeService

object SummarizeController {

  def routes(summarizeService: SummarizeService): AuthedRoutes[User, IO] = {
    AuthedRoutes.of[User, IO] {
      case GET -> Root / "api" / "summarize" :? UrlQueryParamMatcher(url) as user =>
        for {
          summary <- summarizeService.getSummary(url)
          response <- Ok(summary)
        } yield response
    }
  }

  private object UrlQueryParamMatcher extends QueryParamDecoderMatcher[String]("url")

}
