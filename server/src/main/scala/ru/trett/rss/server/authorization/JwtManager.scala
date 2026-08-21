package ru.trett.rss.server.authorization

import pdi.jwt.{JwtAlgorithm, JwtCirce, JwtClaim}
import io.circe.syntax.*
import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import java.time.Clock
import scala.concurrent.duration.*

case class SessionData(userEmail: String)

object SessionData:
    given Decoder[SessionData] = deriveDecoder[SessionData]
    given Encoder[SessionData] = deriveEncoder[SessionData]

class JwtManager(secret: String):
    private val algorithm = JwtAlgorithm.HS256
    private val clock: Clock = Clock.systemUTC()

    def createToken(data: SessionData): String =
        createToken(data, 1.day)

    def createToken(data: SessionData, ttl: FiniteDuration): String =
        val claim =
            JwtClaim(data.asJson.noSpaces)
                .issuedNow(clock)
                .expiresIn(ttl.toSeconds)(clock)
        JwtCirce.encode(claim, secret, algorithm)

    def verifyToken(token: String): Either[Throwable, SessionData] =
        JwtCirce.decodeJson(token, secret, Seq(algorithm)).toEither.flatMap { json =>
            json.as[SessionData]
        }
