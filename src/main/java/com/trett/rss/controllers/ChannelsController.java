package com.trett.rss.controllers;

import org.springframework.web.bind.annotation.RestController;

import com.trett.rss.dao.ChannelRepository;
import com.trett.rss.models.Channel;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;


@RestController
@RequestMapping("/channel")
public class ChannelsController {

    private ChannelRepository channelRepository;
    
    @Autowired
    public ChannelsController(ChannelRepository channelRepository) {
        this.channelRepository = channelRepository;
    }

    @PostMapping(value="/add")
    public void addChannel(@RequestBody String link) {
        Channel channel = new Channel();
        channel.setLink(link);
        channelRepository.save(channel);
    }
}