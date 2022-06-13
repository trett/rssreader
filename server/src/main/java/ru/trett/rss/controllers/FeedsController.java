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
import ru.trett.rss.core.UserService;
import ru.trett.rss.dao.ChannelRepository;
import ru.trett.rss.dao.UserRepository;
import ru.trett.rss.models.User;

import java.security.Principal;
import java.util.Arrays;
import java.util.List;

import javax.validation.constraints.NotNull;

@RestController
@RequestMapping(path = "/api/feed")
public class FeedsController {

    private static final Logger LOG = LoggerFactory.getLogger(FeedsController.class);
    private final ChannelRepository channelRepository;
    private final UserRepository userRepository;
    private final FeedService feedService;
    private final UserService userService;

    @Autowired
    public FeedsController(
            ChannelRepository channelRepository,
            UserRepository userRepository,
            FeedService feedService,
            UserService userService) {
        this.channelRepository = channelRepository;
        this.userRepository = userRepository;
        this.feedService = feedService;
        this.userService = userService;
    }

    @GetMapping(path = "/all", produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    @JsonSerialize(using = IterableSerializer.class)
    public List<FeedEntity> getNews(Principal principal) {
        String userName = principal.getName();
        LOG.info("Retrieving all feeds for principal: " + userName);
        var user = userRepository.findByPrincipalName(userName);
        var items = feedService.getItemsByUserName(userName, user.getSettings().isHideRead());
        LOG.info("Retrived " + items.size() + " feeds");
        return items;
    }

    @GetMapping(path = "/get/{id}", produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    public Iterable<FeedEntity> getFeedsByChannelId(
            @PathVariable @NotNull Long id, Principal principal) {
        LOG.info("Retrieving feeds for channel: " + id);
        User user = userRepository.findByPrincipalName(principal.getName());
        return feedService.getItemsByUserNameAndChannelId(
                principal.getName(), id, user.getSettings().isHideRead());
    }

    @PostMapping(path = "/read")
    public void setRead(@RequestBody @NotNull Long[] ids, Principal principal) {
        LOG.info("Marking feeds with ids " + Arrays.toString(ids) + " as read");
        feedService.markAsRead(Arrays.asList(ids), principal.getName());
    }

    @PostMapping("/deleteOldItems")
    public void deleteOldItems(Principal principal) {
        String userName = principal.getName();
        LOG.info("Deleting old feeds for principal: " + userName);
        var user = userService.getUser(userName);
        var deleted = feedService.deleteOldFeeds(userName, user.getSettings().getDeleteAfter());
        LOG.info(deleted + " feeds was deleted");
    }
}
