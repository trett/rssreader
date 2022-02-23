package ru.trett.rss.dao;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import ru.trett.rss.models.Channel;
import ru.trett.rss.models.User;

@Repository
public interface ChannelRepository extends CrudRepository<Channel, Long> {

    Iterable<Channel> findByUser(User user);

    @Query("SELECT DISTINCT c FROM Channel c LEFT OUTER JOIN FETCH c.feedItems WHERE c.user = ?1")
    Iterable<Channel> findByUserEager(User user);
}
