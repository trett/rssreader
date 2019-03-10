package com.trett.rss.dao;

import com.trett.rss.models.Channel;

import org.springframework.data.repository.CrudRepository;

public interface ChannelRepository extends CrudRepository<Channel, Long> {
    
}