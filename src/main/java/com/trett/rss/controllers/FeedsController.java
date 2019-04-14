package com.trett.rss.controllers;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.IterableSerializer;
import com.trett.rss.dao.ChannelRepository;
import com.trett.rss.dao.FeedItemRepository;
import com.trett.rss.dao.UserRepository;
import com.trett.rss.models.Channel;
import com.trett.rss.models.FeedItem;
import com.trett.rss.models.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.NotEmpty;
import java.security.Principal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@RestController
@RequestMapping(path = "/feed")
public class FeedsController {

    private final ChannelRepository channelRepository;

    private final FeedItemRepository feedItemRepository;

    private final UserRepository userRepository;

    @Autowired
    public FeedsController(ChannelRepository channelRepository, FeedItemRepository feedItemRepository,
                           UserRepository userRepository) {
        this.channelRepository = channelRepository;
        this.feedItemRepository = feedItemRepository;
        this.userRepository = userRepository;
    }

    @GetMapping(path = "/all", produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    @JsonSerialize(using = IterableSerializer.class)
    public List<FeedItem> getNews(Principal principal) {
        return StreamSupport
                .stream(channelRepository
                        .findByUser(userRepository.findByPrincipalName(principal.getName())).spliterator(), false)
                .flatMap(channel -> channel.getFeedItems().stream())
                .collect(Collectors.toList());
    }

    @GetMapping(path = "/get/{id}", produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    public Iterable<FeedItem> getFeedsByChannelId(@PathVariable @NotEmpty String id, Principal principal) {
        User user = userRepository.findByPrincipalName(principal.getName());
        long channelId = Long.parseLong(id);
        boolean hasChannel = false;
        for (Channel channel : user.getChannels()) {
            hasChannel |= channelId == channel.getId();
        }
        if (!hasChannel) {
            throw new RuntimeException("Channel not found");
        }
        return user.getSettings().isHideRead() ?
                feedItemRepository.findAllByChannelIdAndReadIsFalseOrderByPubDateDesc(channelId) :
                feedItemRepository.findAllByChannelIdOrderByPubDateDesc(channelId);

    }

    @PostMapping(path = "/read")
    public void setRead(@RequestBody @NotEmpty String id) {
        Optional<FeedItem> feedItem = feedItemRepository.findById(Long.parseLong(id));
        feedItem.ifPresent(item -> {
            item.setRead(true);
            feedItemRepository.save(item);
        });
    }
}
