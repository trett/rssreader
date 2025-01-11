package ru.trett.server.services

import ru.trett.server.repositories.ChannelRepository
import ru.trett.server.authorization.SessionManager
import cats.effect.IO

class ChannelService(
    private val channelRepository: ChannelRepository,
    private val sessionManager: SessionManager[IO]
) {
  def getChannels(): String = {
    "channels"
  }

  def refreshChannel(): String = {
    "channel: "
  }

  def createChannel(url: String): String = {
    "channel: "
  }

  def deleteChannel(id: String): String = {
    "channel: "
  }

}
