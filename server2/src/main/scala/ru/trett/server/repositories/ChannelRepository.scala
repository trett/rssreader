package ru.trett.server.repositories

import cats.effect.IO
import doobie.*
import doobie.implicits.*
import doobie.util.transactor.Transactor
import ru.trett.server.models.Channel
import ru.trett.server.models.User

class ChannelRepository(transactor: Transactor[IO]) {

  def insertChannel(channel: Channel): IO[Int] = {
    sql"""INSERT INTO channels (id, channelLink, title, link) 
      VALUES (${channel.id}, ${channel.channelLink}, ${channel.title}, ${channel.link})""".update.run
      .transact(transactor)
  }

  def findChannelById(id: Long): IO[Option[Channel]] = {
    sql"SELECT id, channelLink, title, link FROM channels WHERE id = $id"
      .query[Channel]
      .option
      .transact(transactor)
  }

  def findAllChannels(user: User): IO[List[Channel]] = {
    sql"""SELECT id, channelLink, title, link FROM channels c
      JOIN feeds f ON c.id = f.channelId
      where c.user_principal_name = ${user.name}"""
      .query[Channel]
      .to[List]
      .transact(transactor)
  }

  def deleteChannel(id: Long): IO[Int] = {
    sql"DELETE FROM channels WHERE id = $id".update.run
      .transact(transactor)
  }
}
