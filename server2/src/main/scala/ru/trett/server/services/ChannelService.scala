package ru.trett.server.services

import cats.effect.IO
import ru.trett.server.models.Channel
import ru.trett.server.repositories.ChannelRepository
import ru.trett.server.models.User

class ChannelService(channelRepository: ChannelRepository) {

  def createChannel(channelLink: String, title: String, link: String): IO[Int] = {
    val channel = Channel(0, channelLink, title, link)
    channelRepository.insertChannel(channel)
  }

  def getChannelById(id: Long): IO[Option[Channel]] = {
    channelRepository.findChannelById(id)
  }

  def getAllChannels(user: User): IO[List[Channel]] = {
    channelRepository.findAllChannels(user)
  }

  def removeChannel(id: Long): IO[Int] = {
    channelRepository.deleteChannel(id)
  }
}
