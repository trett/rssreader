package ru.trett.rss.dao;

import org.springframework.data.repository.CrudRepository;

import ru.trett.rss.models.Channel;
import ru.trett.rss.models.User;

public interface ChannelRepository extends CrudRepository<Channel, Long> {

    Iterable<Channel> findByUser(User user);
}
