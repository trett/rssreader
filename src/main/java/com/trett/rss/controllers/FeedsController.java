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

import javax.validation.constraints.NotNull;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
        Stream<FeedItem> feedItemStream = StreamSupport
                .stream(channelRepository
                        .findByUser(userRepository.findByPrincipalName(principal.getName())).spliterator(), false)
                .flatMap(channel -> channel.getFeedItems().stream())
                .sorted(Comparator.comparing(FeedItem::getPubDate).reversed());
        if (userRepository.findByPrincipalName(principal.getName()).getSettings().isHideRead()) {
            return feedItemStream.filter(feedItem -> !feedItem.isRead()).collect(Collectors.toList());
        }
        return feedItemStream.collect(Collectors.toList());
    }

    @GetMapping(path = "/get/{id}", produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    public Iterable<FeedItem> getFeedsByChannelId(@PathVariable @NotNull Long id, Principal principal) {
        User user = userRepository.findByPrincipalName(principal.getName());
        boolean hasChannel = false;
        for (Channel channel : user.getChannels()) {
            hasChannel |= id == channel.getId();
        }
        if (!hasChannel) {
            throw new RuntimeException("Channel not found");
        }
        return feedItemRepository.findByChannelIdOrderByPubDateDesc(id);
    }

    @PostMapping(path = "/read")
    public void setRead(@RequestBody @NotNull Long[] ids) {
        Iterable<FeedItem> feedItems = feedItemRepository.findAllById(Arrays.asList(ids));
        feedItems.forEach(item -> item.setRead(true));
        feedItemRepository.saveAll(feedItems);
    }

    @PostMapping("/deleteOldItems")
    public void deleteOldItems(Principal principal) {
        int deleteAfter = userRepository.findByPrincipalName(principal.getName()).getSettings().getDeleteAfter();
        feedItemRepository.deleteFeedsOlderThan(LocalDateTime.now().minusDays(deleteAfter));
    }
}
