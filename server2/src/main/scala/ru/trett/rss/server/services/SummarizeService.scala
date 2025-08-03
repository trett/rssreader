package ru.trett.rss.server.services

import cats.effect.IO
import org.http4s.client.Client
import org.http4s.client.dsl.io._
import org.http4s.Method.GET

class SummarizeService(summarizer: Summarizer, client: Client[IO]) {

  def getSummary(url: String): IO[String] = {
    for {
      uri <- IO.fromEither(org.http4s.Uri.fromString(url))
      text <- client.expect[String](GET(uri))
      summary <- summarizer.summarize(text)
    } yield summary
  }

}
