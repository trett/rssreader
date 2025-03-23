package ru.trett.server.controllers

import cats.effect._
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.circe._
import io.circe.generic.auto._
import org.http4s.client._
import org.http4s.ember.client._
import org.http4s.headers.Location
import org.http4s.implicits._
import org.http4s.headers.Authorization
import ru.trett.server.authorization._
import ru.trett.server.config.OAuthConfig
import ru.trett.server.services.UserService

object LoginController {

  private case class GoogleOAuthConfig(
      clientId: String,
      clientSecret: String,
      redirectUri: String
  )
  private case class OAuthResponse(access_token: String)
  private case class UserInfo(id: String, name: String, email: String)

  private given oauthResponseEntityDecoder: EntityDecoder[IO, OAuthResponse] =
    jsonOf
  private given userInfoResponseEntityDecoder: EntityDecoder[IO, UserInfo] =
    jsonOf

  private val baseUrl = "https://accounts.google.com/o/oauth2/v2/auth"

  object CodeQueryParamMatcher extends QueryParamDecoderMatcher[String]("code")

  def routes(
      sessionManager: SessionManager[IO],
      oauthConfig: OAuthConfig,
      userService: UserService
  ): HttpRoutes[IO] =
    HttpRoutes.of[IO] {
      case GET -> Root / "signin" =>
        val authUri = Uri
          .unsafeFromString(baseUrl)
          .withQueryParams(
            Map(
              "client_id" -> oauthConfig.clientId,
              "redirect_uri" -> (oauthConfig.redirectUri + "/signin_callback"),
              "response_type" -> "code",
              "scope" -> "openid email profile"
            )
          )
        SeeOther(Location(authUri))

      case GET -> Root / "signup" =>
        val authUri = Uri
          .unsafeFromString(baseUrl)
          .withQueryParams(
            Map(
              "client_id" -> oauthConfig.clientId,
              "redirect_uri" -> (oauthConfig.redirectUri + "/signup_callback"),
              "response_type" -> "code",
              "scope" -> "openid email profile"
            )
          )
        SeeOther(Location(authUri))

      case GET -> Root / "signin_callback" :? CodeQueryParamMatcher(code) =>
        val client = EmberClientBuilder.default[IO].build
        client.use { c =>
          for {
            token <- getToken(c, code, oauthConfig, oauthConfig.redirectUri + "/signin_callback")
            userInfo <- getUserInfo(c, token.access_token)
            sessionData = SessionData(
              userEmail = userInfo.email,
              token = "remove later"
            )
            sessionId <- sessionManager.createSession(sessionData)
            response <- SeeOther(Location(uri"/api/channels"))
              .map(
                _.addCookie(
                  ResponseCookie(
                    "sessionId",
                    sessionId,
                    httpOnly = true,
                    secure = true,
                    maxAge = Some(360 * 6)
                  )
                )
              )
          } yield response
        }

      case GET -> Root / "signup_callback" :? CodeQueryParamMatcher(code) =>
        val client = EmberClientBuilder.default[IO].build
        client.use { c =>
          for {
            token <- getToken(c, code, oauthConfig, oauthConfig.redirectUri + "/signup_callback") 
            userInfo <- getUserInfo(c, token.access_token)
            sessionData = SessionData(
              userEmail = userInfo.email,
              token = "remove later"
            )
            _ <- userService.createUser(userInfo.id, userInfo.name, userInfo.email)
            response <- SeeOther(Location(uri"/"))
          } yield response
        }

      // case req @ GET -> Root / "protected" =>
      // req.cookies.find(_.name == "sessionId") match {
      // case Some(cookie) =>
      // sessionManager.getSession(cookie.content).flatMap {
      // case Some(sessionData) =>
      // Ok(s"Welcome ${sessionData.userEmail}, you are logged in!")
      // case None => Forbidden("Invalid session")
      // }
      // case None => Forbidden("No session")
      // }

      case req @ POST -> Root / "logout" =>
        req.cookies.find(_.name == "sessionId") match {
          case Some(cookie) =>
            sessionManager.deleteSession(cookie.content) *> Ok("Logged out")
          case None => BadRequest("No session to log out from")
        }
    }

  private def googleAuthUrl(config: OAuthConfig): Uri =
    Uri
      .unsafeFromString(baseUrl)
      .withQueryParams(
        Map(
          "client_id" -> config.clientId,
          "redirect_uri" -> config.redirectUri,
          "response_type" -> "code",
          "scope" -> "openid email profile"
        )
      )

  private def getToken(
      client: Client[IO],
      code: String,
      config: OAuthConfig,
      redirectUri: String
  ): IO[OAuthResponse] =
    val tokenUri = Uri.unsafeFromString("https://oauth2.googleapis.com/token")
    val request = Request[IO](Method.POST, tokenUri).withEntity(
      UrlForm(
        "code" -> code,
        "client_id" -> config.clientId,
        "client_secret" -> config.clientSecret,
        "redirect_uri" -> redirectUri,
        "grant_type" -> "authorization_code"
      )
    )
    client.expect[OAuthResponse](request)

  private def getUserInfo(client: Client[IO], token: String): IO[UserInfo] =
    val userInfoUri = Uri.unsafeFromString(
      "https://www.googleapis.com/oauth2/v1/userinfo?alt=json"
    )
    val request = Request[IO](Method.GET, userInfoUri)
      .withHeaders(Authorization(Credentials.Token(AuthScheme.Bearer, token)))
    client.expect[UserInfo](request)
}
