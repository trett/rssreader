package ru.trett.rss.controllers;

import com.rometools.rome.io.FeedException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import ru.trett.rss.core.ChannelService;
import ru.trett.rss.core.ClientException;
import ru.trett.rss.core.FeedService;
import ru.trett.rss.core.UserService;
import ru.trett.rss.models.Channel;
import ru.trett.rss.parser.RssParser;

import java.net.URI;
import java.security.Principal;
import java.text.MessageFormat;
import java.time.LocalDateTime;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

@RestController
@RequestMapping(path = "/api/channel")
public class ChannelsController {

    private static final Logger LOG = LoggerFactory.getLogger(ChannelsController.class);

    private final ChannelService channelService;
    private final FeedService feedService;
    private final UserService userService;
    private final RssParser rssParser;
    private final RestTemplate restTemplate;

    @Autowired
    public ChannelsController(
            ChannelService channelService,
            FeedService feedService,
            UserService userService,
            RssParser rssParser,
            RestTemplate restTemplate) {
        this.channelService = channelService;
        this.feedService = feedService;
        this.userService = userService;
        this.rssParser = rssParser;
        this.restTemplate = restTemplate;
    }

    @GetMapping(path = "/all")
    public Iterable<Channel> getChannels(Principal principal) {
        var userName = principal.getName();
        LOG.info("Receiving channels for the user: " + userName);
        return userService
                .getUser(userName)
                .map(user -> channelService.findByUser(user.principalName))
                .orElse(null);
    }

    @GetMapping(path = "/refresh")
    public void refresh(Principal principal) {
        var userName = principal.getName();
        var logMessage = "Updating channels for the user: " + userName;
        LOG.info(logMessage + "Start");
        var maybeUser = userService.getUser(userName);
        if (maybeUser.isEmpty()) {
            return;
        }
        var user = maybeUser.get();
        for (var channel : channelService.findByUser(user.principalName)) {
            LOG.info("Starting an update feeds for the channel: " + channel.title);
            var updatedFeeds =
                    restTemplate.execute(
                            URI.create(channel.channelLink),
                            HttpMethod.GET,
                            null,
                            response -> {
                                var deleteAfter = user.settings.deleteAfter;
                                var since = LocalDateTime.now().minusDays(deleteAfter);
                                try {
                                    var feeds =
                                            rssParser.parse(response.getBody()).feedItems.stream()
                                                    .filter(feed -> feed.pubDate.isAfter(since))
                                                    .toList();
                                    return feedService.saveAll(feeds, channel.id);
                                } catch (HttpClientErrorException | FeedException e) {
                                    throw new ClientException(
                                            "Can't update feeds. Please try later", e);
                                }
                            });
            LOG.info(
                    MessageFormat.format(
                            "Updated {0} feeds for channel ''{1}''", updatedFeeds, channel.title));
        }
        LOG.info(logMessage + "End");
    }

    @PostMapping(path = "/add")
    public void add(@RequestBody @NotEmpty String link, Principal principal) {
        var trimmedLink = link.trim();
        LOG.info("Adding channel with link: " + link);
        userService
                .getUser(principal.getName())
                .ifPresent(
                        user -> {
                            if (channelService.findByUser(user.principalName).stream()
                                    .anyMatch(channel -> channel.channelLink.equals(trimmedLink))) {
                                throw new RuntimeException("Channel already exist");
                            }
                            restTemplate.execute(
                                    URI.create(link),
                                    HttpMethod.GET,
                                    null,
                                    response -> {
                                        try {
                                            var channel = rssParser.parse(response.getBody());
                                            channel.channelLink = trimmedLink;
                                            return channelService.save(channel, user.principalName);
                                        } catch (FeedException e) {
                                            throw new ClientException("URL is not valid");
                                        }
                                    });
                        });
    }

    @PostMapping(path = "/delete")
    public String delete(@NotNull @RequestBody Long id, Principal principal) {
        LOG.info("Deleting channel with id: " + id);
        return userService
                .getUser(principal.getName())
                .map(
                        user -> {
                            var userName = user.principalName;
                            for (Channel channel : channelService.findByUser(userName)) {
                                var channelId = channel.id;
                                if (channelId == id) {
                                    feedService.deleteFeedsByChannel(userName, channelId);
                                    channelService.delete(id);
                                    break;
                                }
                            }
                            return "deleted: " + id;
                        })
                .orElseThrow(() -> new ClientException("Channel not found"));
    }
}
