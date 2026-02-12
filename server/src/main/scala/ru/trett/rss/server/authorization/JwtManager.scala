package ru.trett.rss.server.authorization

import pdi.jwt.{JwtAlgorithm, JwtCirce, JwtClaim}
import io.circe.syntax.*
import io.circe.generic.auto.*
import java.time.Clock

case class SessionData(userEmail: String)

class JwtManager(secret: String):
    private val algorithm = JwtAlgorithm.HS256
    private val clock: Clock = Clock.systemUTC()

    def createToken(data: SessionData): String =
        val claim =
            JwtClaim(data.asJson.noSpaces).issuedNow(clock).expiresIn(60 * 60 * 24)(clock) // 1 day
        JwtCirce.encode(claim, secret, algorithm)

    def verifyToken(token: String): Either[Throwable, SessionData] =
        JwtCirce.decodeJson(token, secret, Seq(algorithm)).toEither.flatMap { json =>
            json.as[SessionData]
        }
