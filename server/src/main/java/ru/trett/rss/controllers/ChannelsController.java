package ru.trett.rss.controllers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import ru.trett.rss.core.ChannelService;
import ru.trett.rss.core.ClientException;
import ru.trett.rss.core.FeedService;
import ru.trett.rss.core.UserService;
import ru.trett.rss.models.Channel;
import ru.trett.rss.parser.RssParser;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.security.Principal;
import java.text.MessageFormat;
import java.time.LocalDateTime;
import java.util.stream.Collectors;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

@RestController
@RequestMapping(path = "/api/channel")
public class ChannelsController {

    private static final Logger LOG = LoggerFactory.getLogger(ChannelsController.class);

    private final ChannelService channelService;
    private final FeedService feedService;
    private final UserService userService;
    private final RestTemplate restTemplate;

    @Autowired
    public ChannelsController(
            ChannelService channelService,
            FeedService feedService,
            UserService userRepository,
            RestTemplate restTemplate) {
        this.channelService = channelService;
        this.feedService = feedService;
        this.userService = userRepository;
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
        var requestFactory = restTemplate.getRequestFactory();
        var maybeUser = userService.getUser(userName);
        if (maybeUser.isEmpty()) {
            return;
        }
        var user = maybeUser.get();
        try {
            for (var channel : channelService.findByUser(user.principalName)) {
                LOG.info("Starting an update feeds for the channel: " + channel.title);
                var request =
                        requestFactory.createRequest(
                                URI.create(channel.channelLink), HttpMethod.GET);
                var execute = request.execute();
                var deleteAfter = user.settings.deleteAfter;
                try (var is = execute.getBody()) {
                    var since = LocalDateTime.now().minusDays(deleteAfter);
                    var feeds =
                            new RssParser(is)
                                    .parse().feedItems.stream()
                                            .filter(feed -> feed.pubDate.isAfter(since))
                                            .collect(Collectors.toList());
                    var inserted = feedService.saveAll(feeds, channel.id);
                    LOG.info(
                            MessageFormat.format(
                                    "{0} items was updated for ''{1}''", inserted, channel.title));
                }
            }
        } catch (Exception e) {
            throw new ClientException("Can't update feeds. Please try later", e);
        }
        LOG.info(logMessage + "End");
    }

    @PostMapping(path = "/add")
    public void add(@RequestBody @NotEmpty String link, Principal principal) throws IOException {
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
                            try {
                                var request =
                                        restTemplate
                                                .getRequestFactory()
                                                .createRequest(URI.create(link), HttpMethod.GET);
                                try (InputStream inputStream = request.execute().getBody()) {
                                    var channel = new RssParser(inputStream).parse();
                                    channel.channelLink = trimmedLink;
                                    channelService.save(channel, user.principalName);
                                }
                            } catch (IOException e) {
                                throw new ClientException("URL is not valid");
                            }
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
