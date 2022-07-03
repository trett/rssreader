package ru.trett.rss.controllers;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.IterableSerializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import ru.trett.rss.core.FeedService;
import ru.trett.rss.core.UserService;
import ru.trett.rss.models.Feed;

import java.security.Principal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.validation.constraints.NotNull;

@RestController
@RequestMapping(path = "/api/feed")
public class FeedsController {

    private static final Logger LOG = LoggerFactory.getLogger(FeedsController.class);
    private final FeedService feedService;
    private final UserService userService;

    @Autowired
    public FeedsController(FeedService feedService, UserService userService) {
        this.feedService = feedService;
        this.userService = userService;
    }

    @GetMapping(path = "/all", produces = MediaType.APPLICATION_JSON_VALUE)
    @JsonSerialize(using = IterableSerializer.class)
    public List<Feed> getNews(Principal principal) {
        String userName = principal.getName();
        LOG.info("Retrieving all feeds for principal: " + userName);
        var items =
                userService
                        .getUser(userName)
                        .map(
                                user ->
                                        feedService.getItemsByUserName(
                                                userName, user.settings.hideRead))
                        .orElse(Collections.emptyList());
        LOG.info("Retrived " + items.size() + " feeds");
        return items;
    }

    @GetMapping(path = "/get/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Iterable<Feed> getFeedsByChannelId(@PathVariable @NotNull Long id, Principal principal) {
        LOG.info("Retrieving feeds for channel: " + id);
        return userService
                .getUser(principal.getName())
                .map(
                        user ->
                                feedService.getItemsByUserNameAndChannelId(
                                        principal.getName(), id, user.settings.hideRead))
                .orElse(Collections.emptyList());
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
        userService
                .getUser(userName)
                .ifPresent(
                        user -> {
                            var deleted =
                                    feedService.deleteOldFeeds(userName, user.settings.deleteAfter);
                            LOG.info(deleted + " feeds was deleted");
                        });
    }
}
