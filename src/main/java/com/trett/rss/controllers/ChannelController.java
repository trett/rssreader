package com.trett.rss.controllers;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.impl.IndexedListSerializer;
import com.fasterxml.jackson.databind.ser.std.IterableSerializer;
import com.trett.rss.dao.ChannelRepository;
import com.trett.rss.dao.FeedItemRepository;
import com.trett.rss.models.Channel;
import com.trett.rss.models.FeedItem;
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
import java.util.Optional;

@RestController
@RequestMapping(path = "/channels")
public class ChannelController {

    private final ChannelRepository channelRepository;


    private final FeedItemRepository feedItemRepository;

    @Autowired
    public ChannelController(ChannelRepository channelRepository, FeedItemRepository feedItemRepository) {
        this.channelRepository = channelRepository;
        this.feedItemRepository = feedItemRepository;
    }

    @GetMapping(path = "/refresh")
    @JsonSerialize(using = IndexedListSerializer.class)
    public void refresh() throws IOException, XMLStreamException, URISyntaxException {
        RestTemplate restTemplate = new RestTemplate();
        ClientHttpRequestFactory requestFactory = restTemplate.getRequestFactory();
        for (Channel channel : channelRepository.findAll()) {
            ClientHttpRequest request = requestFactory.createRequest(URI.create(channel.getChannelLink()), HttpMethod.GET);
            ClientHttpResponse execute = request.execute();
            try (InputStream inputStream = execute.getBody()) {
                feedItemRepository.saveAll(new RssParser(inputStream).parse(channel).getFeedItems());
            }
        }
    }

    @GetMapping(path = "/all", produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    @JsonSerialize(using = IterableSerializer.class)
    public Iterable<Channel> getNews() throws IOException, XMLStreamException, URISyntaxException {
        return channelRepository.findAll();
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
    public void addFeed(@RequestBody @NotEmpty String link) throws IOException, XMLStreamException {
        ClientHttpRequest request = new RestTemplate().getRequestFactory()
                .createRequest(URI.create(link), HttpMethod.GET);
        try (InputStream inputStream = request.execute().getBody()) {
            Channel channel = new Channel();
            new RssParser(inputStream).parse(channel);
            channel.setChannelLink(link);
            channelRepository.save(channel);
        }
    }
}
