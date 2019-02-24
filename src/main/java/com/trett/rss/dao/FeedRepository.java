package com.trett.rss.dao;

import com.trett.rss.models.Feed;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FeedRepository extends CrudRepository<Feed, Long> {
}
