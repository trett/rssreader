package com.trett.rss.dao;

import com.trett.rss.models.Channel;
import com.trett.rss.models.User;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ChannelRepository extends CrudRepository<Channel, Long> {

    Iterable<Channel> findByUser(User user);

    @Query("SELECT DISTINCT c FROM Channel c JOIN FETCH c.feedItems WHERE c.user = ?1")
    Iterable<Channel> findByUserEager(User user);
}
