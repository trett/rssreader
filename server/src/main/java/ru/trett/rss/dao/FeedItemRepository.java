package ru.trett.rss.dao;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import ru.trett.rss.models.FeedItem;

@Repository
public interface FeedItemRepository extends CrudRepository<FeedItem, Long> {}
