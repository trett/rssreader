package ru.trett.rss.server.services

import cats.effect.IO
import io.circe.generic.auto._
import io.circe.syntax._
import org.http4s.circe.CirceEntityCodec._
import org.http4s.client.Client
import org.http4s.{Method, Request, Uri}

case class Prompt(text: String)
case class Candidate(content: String)
case class Completion(candidates: List[Candidate])

class Summarizer(apiKey: String, client: Client[IO]) {

    private val endpoint = Uri.unsafeFromString(
        s"https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:generateContent?key=$apiKey"
    )

    def summarize(text: String): IO[String] = {
        val request = Request[IO](method = Method.POST, uri = endpoint).withEntity(
            Map(
                "contents" -> List(
                    Map("parts" -> List(Map("text" -> s"Summarize the following text: $text")))
                )
            ).asJson
        )

        client
            .expect[Completion](request)
            .map { completion =>
                completion.candidates.headOption.map(_.content).getOrElse("No summary available.")
            }
            .handleErrorWith { error =>
                IO(println(s"Error: $error")) *> IO.pure("Error summarizing text.")
            }
    }
}
