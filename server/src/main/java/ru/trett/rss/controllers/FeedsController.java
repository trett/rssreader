package ru.trett.rss.controllers;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.IterableSerializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import ru.trett.rss.core.FeedEntity;
import ru.trett.rss.core.FeedService;
import ru.trett.rss.dao.ChannelRepository;
import ru.trett.rss.dao.FeedItemRepository;
import ru.trett.rss.dao.UserRepository;
import ru.trett.rss.models.Channel;
import ru.trett.rss.models.FeedItem;
import ru.trett.rss.models.User;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import javax.validation.constraints.NotNull;

@RestController
@RequestMapping(path = "/api/feed")
public class FeedsController {

    private static final Logger logger = LoggerFactory.getLogger(FeedsController.class);
    private final ChannelRepository channelRepository;
    private final FeedItemRepository feedItemRepository;
    private final UserRepository userRepository;
    private final FeedService feedService;

    @Autowired
    public FeedsController(
            ChannelRepository channelRepository,
            FeedItemRepository feedItemRepository,
            UserRepository userRepository,
            FeedService feedService) {
        this.channelRepository = channelRepository;
        this.feedItemRepository = feedItemRepository;
        this.userRepository = userRepository;
        this.feedService = feedService;
    }

    @GetMapping(path = "/all", produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    @JsonSerialize(using = IterableSerializer.class)
    public List<FeedEntity> getNews(Principal principal) {
        String userName = principal.getName();
        logger.info("Retrieving all feeds for principal: " + userName);
        var user = userRepository.findByPrincipalName(userName);
        var items = feedService.getItemsByUserName(userName, user.getSettings().isHideRead());
        logger.info("Retrived " + items.size() + " feeds");
        return items;
    }

    @GetMapping(path = "/get/{id}", produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    public Iterable<FeedItem> getFeedsByChannelId(
            @PathVariable @NotNull Long id, Principal principal) {
        logger.info("Retrieving feeds for channel: " + id);
        User user = userRepository.findByPrincipalName(principal.getName());
        boolean hasChannel = false;
        // checking rights for channel
        for (Channel channel : channelRepository.findByUser(user)) {
            hasChannel |= id == channel.getId();
        }
        if (!hasChannel) {
            throw new RuntimeException("Channel not found");
        }
        Iterable<FeedItem> feedItems = feedItemRepository.findByChannelIdOrderByPubDateDesc(id);
        return user.getSettings().isHideRead()
                ? StreamSupport.stream(feedItems.spliterator(), false)
                        .filter(item -> !item.isRead())
                        .collect(Collectors.toList())
                : feedItems;
    }

    @PostMapping(path = "/read")
    public void setRead(@RequestBody @NotNull Long[] ids) {
        logger.info("Marking feeds with ids " + Arrays.toString(ids) + " as read");
        Iterable<FeedItem> feedItems = feedItemRepository.findAllById(Arrays.asList(ids));
        feedItems.forEach(item -> item.setRead(true));
        feedItemRepository.saveAll(feedItems);
    }

    @PostMapping("/deleteOldItems")
    public void deleteOldItems(Principal principal) {
        String userName = principal.getName();
        logger.info("Deleting old feeds for principal: " + userName);
        User user = userRepository.findByPrincipalName(userName);
        channelRepository
                .findByUserEager(user)
                .forEach(
                        channel ->
                                channel.getFeedItems().stream()
                                        .filter(
                                                feedItem ->
                                                        feedItem.getPubDate()
                                                                .isBefore(
                                                                        LocalDateTime.now()
                                                                                .minusDays(
                                                                                        user.getSettings()
                                                                                                .getDeleteAfter())))
                                        .forEach(feedItem -> feedItemRepository.delete(feedItem)));
    }
}
