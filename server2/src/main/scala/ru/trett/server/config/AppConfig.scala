package ru.trett.server.config

import pureconfig.*
import pureconfig.generic.derivation.default.*

import scala.concurrent.duration.FiniteDuration

case class AppConfig(
  server: ServerConfig,
  db: DbConfig,
  oauth: OAuthConfig,
  cors: CorsConfig
) derives ConfigReader

case class ServerConfig(
  port: Int,
  host: String = "0.0.0.0"
) derives ConfigReader

case class DbConfig(
  driver: String,
  url: String,
  user: String,
  password: String
) derives ConfigReader

case class OAuthConfig(
  clientId: String,
  clientSecret: String,
  redirectUri: String
) derives ConfigReader

case class CorsConfig(
  allowedOrigin: String,
  allowCredentials: Boolean,
  maxAge: FiniteDuration
) derives ConfigReader