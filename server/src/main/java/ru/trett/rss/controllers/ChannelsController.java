package ru.trett.rss.controllers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import ru.trett.rss.core.ChannelService;
import ru.trett.rss.core.ClientException;
import ru.trett.rss.core.FeedService;
import ru.trett.rss.core.UserService;
import ru.trett.rss.models.Channel;
import ru.trett.rss.models.User;
import ru.trett.rss.parser.RssParser;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.security.Principal;
import java.text.MessageFormat;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

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
        String userName = principal.getName();
        LOG.info("Retrieving all channels for principal: " + userName);
        return userService
                .getUser(userName)
                .map(user -> channelService.findByUser(user.getPrincipalName()))
                .orElse(null);
    }

    @GetMapping(path = "/refresh")
    public void refresh(Principal principal) {
        String userName = principal.getName();
        String logMessage = "Updating channels for principal: " + userName + ". ";
        LOG.info(logMessage + "Start");
        ClientHttpRequestFactory requestFactory = restTemplate.getRequestFactory();
        Optional<User> maybeUser = userService.getUser(userName);
        if (maybeUser.isEmpty()) {
            return;
        }
        User user = maybeUser.get();
        try {
            for (Channel channel : channelService.findByUser(user.getPrincipalName())) {
                LOG.info("Starting update feeds for channel: " + channel.getTitle());
                ClientHttpRequest request =
                        requestFactory.createRequest(
                                URI.create(channel.getChannelLink()), HttpMethod.GET);
                ClientHttpResponse execute = request.execute();
                int deleteAfter = user.getSettings().getDeleteAfter();
                try (InputStream inputStream = execute.getBody()) {
                    var since = LocalDateTime.now().minusDays(deleteAfter);
                    var feeds =
                            new RssParser(inputStream)
                                    .parse().getFeedItems().stream()
                                            .filter(feed -> feed.getPubDate().isAfter(since))
                                            .collect(Collectors.toList());
                    int inserted = feedService.saveAll(feeds, channel.getId());
                    LOG.info(
                            MessageFormat.format(
                                    "{0} items was updated for ''{1}''",
                                    inserted, channel.getTitle()));
                }
            }
        } catch (Exception e) {
            throw new ClientException("Can't update feeds. Please try later", e);
        }
        LOG.info(logMessage + "End");
    }

    @PostMapping(path = "/add")
    public Long addFeed(@RequestBody @NotEmpty String link, Principal principal)
            throws IOException {
        link = link.trim();
        LOG.info("Adding channel with link: " + link);
        try {
            ClientHttpRequest request =
                    restTemplate
                            .getRequestFactory()
                            .createRequest(URI.create(link), HttpMethod.GET);
            try (InputStream inputStream = request.execute().getBody()) {
                Channel channel = new RssParser(inputStream).parse();
                Optional<User> maybeUser = userService.getUser(principal.getName());
                if (maybeUser.isEmpty()) {
                    throw new RuntimeException("User not found");
                }
                User user = maybeUser.get();
                if (StreamSupport.stream(
                                channelService.findByUser(user.getPrincipalName()).spliterator(),
                                false)
                        .anyMatch(channel::equals)) {
                    throw new RuntimeException("Channel already exist");
                }
                channel.setUser(user);
                channel.setChannelLink(link);
                return channelService.save(channel);
            }
        } catch (IOException e) {
            throw new ClientException("URL is not valid");
        }
    }

    @PostMapping(path = "/delete")
    public String delete(@NotNull @RequestBody Long id, Principal principal) {
        LOG.info("Deleting channel with id: " + id);
        return userService
                .getUser(principal.getName())
                .map(
                        user -> {
                            var userName = user.getPrincipalName();
                            for (Channel channel : channelService.findByUser(userName)) {
                                var channelId = channel.getId();
                                if (channelId == id) {
                                    feedService.deleteFeedsByChannel(userName, channelId);
                                    channelService.delete(id);
                                }
                            }
                            return "deleted: " + id;
                        })
                .orElseThrow(() -> new ClientException("Channel not found"));
    }
}
