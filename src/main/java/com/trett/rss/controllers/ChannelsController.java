package com.trett.rss.controllers;

import com.trett.rss.dao.ChannelRepository;
import com.trett.rss.dao.FeedItemRepository;
import com.trett.rss.dao.UserRepository;
import com.trett.rss.models.Channel;
import com.trett.rss.models.FeedItem;
import com.trett.rss.models.User;
import com.trett.rss.parser.RssParser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.security.Principal;
import java.util.Set;
import java.util.stream.StreamSupport;

@RestController
@RequestMapping(path = "/api/channel")
public class ChannelsController {

    private Logger logger = LoggerFactory.getLogger(ChannelsController.class);

    private ChannelRepository channelRepository;

    private FeedItemRepository feedItemRepository;

    private UserRepository userRepository;

    private RestTemplate restTemplate;

    @Autowired
    public ChannelsController(ChannelRepository channelRepository, FeedItemRepository feedItemRepository,
            UserRepository userRepository, RestTemplate restTemplate) {
        this.channelRepository = channelRepository;
        this.feedItemRepository = feedItemRepository;
        this.userRepository = userRepository;
        this.restTemplate = restTemplate;
    }

    @GetMapping(path = "/all")
    public Iterable<Channel> getChannels(Principal principal) {
        String userName = principal.getName();
        logger.info("Retrieving all feeds for principal: " + userName);
        return channelRepository.findByUser(userRepository.findByPrincipalName(userName));
    }

    @GetMapping(path = "/refresh")
    public void refresh(Principal principal) {
        String userName = principal.getName();
        String logMessage = "Updating channels for principal: " + userName + ". ";
        logger.info(logMessage + "Start");
        ClientHttpRequestFactory requestFactory = restTemplate.getRequestFactory();
        try {
            for (Channel channel : channelRepository
                    .findByUser(userRepository.findByPrincipalName(userName))) {
                logger.info("Starting update feeds for channel: " + channel.getTitle());
                ClientHttpRequest request = requestFactory.createRequest(URI.create(channel.getChannelLink()),
                        HttpMethod.GET);
                ClientHttpResponse execute = request.execute();
                int deleteAfter = userRepository.findByPrincipalName(userName).getSettings()
                        .getDeleteAfter();
                try (InputStream inputStream = execute.getBody()) {
                    Set<FeedItem> items = new RssParser(inputStream).getNewFeeds(channel, deleteAfter);
                    logger.info("Updated " + items.size() + "feeds");
                    feedItemRepository.saveAll(items);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Can't update feeds. Please try later", e);
        }
        logger.info(logMessage + "End");
    }

    @PostMapping(path = "/add")
    public Long addFeed(@RequestBody @NotEmpty String link, Principal principal) throws IOException {
        logger.info("Adding channel with link: " + link);
        try {
            ClientHttpRequest request = restTemplate.getRequestFactory().createRequest(URI.create(link),
                    HttpMethod.GET);
            try (InputStream inputStream = request.execute().getBody()) {
                Channel channel = new RssParser(inputStream).parse();
                User user = userRepository.findByPrincipalName(principal.getName());
                if (StreamSupport.stream(channelRepository.findByUser(user).spliterator(), false)
                        .anyMatch(channel::equals)) {
                    throw new RuntimeException("Channel already exist");
                }
                channel.setUser(user);
                channel.setChannelLink(link);
                return channelRepository.save(channel).getId();
            }
        } catch (IOException e) {
            throw new RuntimeException("URL is not valid");
        }
    }

    @PostMapping(path = "/delete")
    public void delete(@NotNull @RequestBody Long id) {
        logger.info("Deleting channel with id: " + id);
        channelRepository.deleteById(id);
    }
}
