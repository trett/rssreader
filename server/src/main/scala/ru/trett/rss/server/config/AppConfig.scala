package ru.trett.rss.server.config

import pureconfig.*

import scala.concurrent.duration.FiniteDuration

case class AppConfig(
    server: ServerConfig,
    db: DbConfig,
    oauth: OAuthConfig,
    cors: CorsConfig,
    jobs: JobConfig,
    jwt: JwtConfig,
    digest: DigestConfig = DigestConfig()
) derives ConfigReader

case class ServerConfig(port: Int, host: String = "0.0.0.0") derives ConfigReader

case class DbConfig(driver: String, url: String, user: String, password: String)
    derives ConfigReader

case class OAuthConfig(clientId: String, clientSecret: String, redirectUri: String)
    derives ConfigReader

case class CorsConfig(allowedOrigin: String, allowCredentials: Boolean, maxAge: FiniteDuration)
    derives ConfigReader

case class JobConfig(token: String) derives ConfigReader

case class JwtConfig(secret: String) derives ConfigReader

case class DigestConfig(smtpUsername: String = "", smtpPassword: String = "") derives ConfigReader:
    override def toString: String = "DigestConfig(<redacted>)"
