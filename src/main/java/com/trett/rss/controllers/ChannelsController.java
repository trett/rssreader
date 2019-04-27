package com.trett.rss.controllers;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.impl.IndexedListSerializer;
import com.trett.rss.dao.ChannelRepository;
import com.trett.rss.dao.FeedItemRepository;
import com.trett.rss.dao.UserRepository;
import com.trett.rss.models.Channel;
import com.trett.rss.models.User;
import com.trett.rss.parser.RssParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.security.Principal;
import java.util.Arrays;

@RestController
@RequestMapping(path = "/channel")
public class ChannelsController {

    private final ChannelRepository channelRepository;

    private final FeedItemRepository feedItemRepository;

    private final UserRepository userRepository;

    @Autowired
    public ChannelsController(ChannelRepository channelRepository, FeedItemRepository feedItemRepository,
                              UserRepository userRepository) {
        this.channelRepository = channelRepository;
        this.feedItemRepository = feedItemRepository;
        this.userRepository = userRepository;
    }

    @GetMapping(path = "/all")
    public Iterable<Channel> getChannels(Principal principal) {
        return channelRepository.findByUser(userRepository.findByPrincipalName(principal.getName()));
    }

    @GetMapping(path = "/refresh")
    @JsonSerialize(using = IndexedListSerializer.class)
    public void refresh(Principal principal) {
        RestTemplate restTemplate = new RestTemplate();
        ClientHttpRequestFactory requestFactory = restTemplate.getRequestFactory();
        try {
            for (Channel channel : channelRepository.findByUser(userRepository.findByPrincipalName(principal.getName()))) {
                ClientHttpRequest request = requestFactory
                        .createRequest(URI.create(channel.getChannelLink()), HttpMethod.GET);
                ClientHttpResponse execute = request.execute();
                try (InputStream inputStream = execute.getBody()) {
                    feedItemRepository.saveAll(new RssParser(inputStream).geeNewFeeds(channel));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Can't update feeds. Please try later");
        }
    }

    @PostMapping(path = "/add")
    public Long addFeed(@RequestBody @NotEmpty String link, Principal principal)
            throws IOException, XMLStreamException {
        ClientHttpRequest request = new RestTemplate().getRequestFactory()
                .createRequest(URI.create(link), HttpMethod.GET);
        try (InputStream inputStream = request.execute().getBody()) {
            Channel channel = new RssParser(inputStream).parse();
            User user = userRepository.findByPrincipalName(principal.getName());
            channel.setUser(user);
            channel.setChannelLink(link);
            return channelRepository.save(channel).getId();
        }
    }

    @PostMapping(path = "/delete")
    public void delete(@NotNull @RequestBody Long id) {
        channelRepository.deleteById(id);
    }
}
