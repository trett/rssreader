package com.trett.rss.dao;

import com.trett.rss.models.FeedItem;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FeedItemRepository extends CrudRepository<FeedItem, Long> {

    Iterable<FeedItem> findByChannelIdOrderByPubDateDesc(Long id);
}
