package com.trett.rss.dao;

import com.trett.rss.models.FeedItem;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import javax.transaction.Transactional;
import java.time.LocalDateTime;

@Repository
public interface FeedItemRepository extends CrudRepository<FeedItem, Long> {

    Iterable<FeedItem> findAllByChannelIdOrderByPubDateDesc(Long id);

    @Transactional
    @Modifying
    @Query("UPDATE FeedItem fi SET read = TRUE WHERE fi.channel.id = ?1")
    void markAsReadByChannelId(Long id);

    @Transactional
    @Modifying
    @Query("DELETE FROM FeedItem fi WHERE fi.pubDate < ?1")
    void deleteFeedsOlderThan(LocalDateTime date);
}
