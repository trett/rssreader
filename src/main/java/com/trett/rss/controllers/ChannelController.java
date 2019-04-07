package com.trett.rss.controllers;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.impl.IndexedListSerializer;
import com.fasterxml.jackson.databind.ser.std.IterableSerializer;
import com.trett.rss.dao.ChannelRepository;
import com.trett.rss.dao.FeedItemRepository;
import com.trett.rss.dao.UserRepository;
import com.trett.rss.models.Channel;
import com.trett.rss.models.FeedItem;
import com.trett.rss.models.User;
import com.trett.rss.parser.RssParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import javax.validation.constraints.NotEmpty;
import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.Principal;
import java.util.Optional;

@RestController
@RequestMapping(path = "/channels")
public class ChannelController {

    private final ChannelRepository channelRepository;

    private final FeedItemRepository feedItemRepository;

    private final UserRepository userRepository;

    @Autowired
    public ChannelController(ChannelRepository channelRepository, FeedItemRepository feedItemRepository,
                             UserRepository userRepository) {
        this.channelRepository = channelRepository;
        this.feedItemRepository = feedItemRepository;
        this.userRepository = userRepository;
    }

    @GetMapping(path = "/refresh")
    @JsonSerialize(using = IndexedListSerializer.class)
    public void refresh(Principal principal) throws IOException, XMLStreamException, URISyntaxException {
        RestTemplate restTemplate = new RestTemplate();
        ClientHttpRequestFactory requestFactory = restTemplate.getRequestFactory();
        for (Channel channel : channelRepository.findByUser(userRepository.findByPrincipalName(principal.getName()))) {
            ClientHttpRequest request = requestFactory
                    .createRequest(URI.create(channel.getChannelLink()), HttpMethod.GET);
            ClientHttpResponse execute = request.execute();
            try (InputStream inputStream = execute.getBody()) {
                feedItemRepository.saveAll(new RssParser(inputStream).parse(channel).getFeedItems());
            }
        }
    }

    @GetMapping(path = "/all", produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    @JsonSerialize(using = IterableSerializer.class)
    public Iterable<Channel> getNews(Principal principal) throws IOException, XMLStreamException, URISyntaxException {
        return channelRepository.findByUser(
                userRepository.findByPrincipalName(principal.getName())
        );
    }

    @GetMapping(path = "/get/{id}", produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    public Channel getChannelById(@PathVariable @NotEmpty String id) {
        return channelRepository.findById(Long.parseLong(id)).orElseGet(null);
    }

    @PostMapping(path = "/read")
    public void setRead(@RequestBody @NotEmpty String id) {
        Optional<FeedItem> feedItem = feedItemRepository.findById(Long.parseLong(id));
        feedItem.ifPresent(item -> {
            item.setRead(true);
            feedItemRepository.save(item);
        });
    }

    @PostMapping(path = "/add")
    public void addFeed(@RequestBody @NotEmpty String link, Principal principal)
            throws IOException, XMLStreamException {
        ClientHttpRequest request = new RestTemplate().getRequestFactory()
                .createRequest(URI.create(link), HttpMethod.GET);
        try (InputStream inputStream = request.execute().getBody()) {
            Channel channel = new Channel();
            new RssParser(inputStream).parse(channel);
            User user = userRepository.findByPrincipalName(principal.getName());
            channel.setUser(user);
            channel.setChannelLink(link);
            channelRepository.save(channel);
        }
    }
}
