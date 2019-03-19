package com.trett.rss.controllers;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.impl.IndexedListSerializer;
import com.trett.rss.dao.FeedItemRepository;
import com.trett.rss.dao.FeedRepository;
import com.trett.rss.models.Feed;
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
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

@RestController
@RequestMapping(path = "/channels")
public class FeedsController {

    private final FeedRepository feedRepository;


    private final FeedItemRepository feedItemRepository;

    @Autowired
    public FeedsController(FeedRepository feedRepository, FeedItemRepository feedItemRepository) {
        this.feedRepository = feedRepository;
        this.feedItemRepository = feedItemRepository;
    }

    @GetMapping(path = "/refresh")
    @JsonSerialize(using = IndexedListSerializer.class)
    public void refresh() throws IOException, XMLStreamException, URISyntaxException {
        RestTemplate restTemplate = new RestTemplate();
        ClientHttpRequestFactory requestFactory = restTemplate.getRequestFactory();
        for (Feed feed : feedRepository.findAll()) {
            ClientHttpRequest request = requestFactory.createRequest(URI.create(feed.getChannelLink()), HttpMethod.GET);
            ClientHttpResponse execute = request.execute();
            try (InputStream inputStream = execute.getBody()) {
                feedItemRepository.saveAll(new RssParser(inputStream).parse(feed).getFeedItems());
            }
        }
    }

    @GetMapping(path = "/all", produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    @JsonSerialize(using = IndexedListSerializer.class)
    public Set<Feed> getNews() throws IOException, XMLStreamException, URISyntaxException {
        Set<Feed> items = new LinkedHashSet<>();
        Iterable<Feed> all = feedRepository.findAll();
        all.forEach(items::add);
        return items;
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
            Feed feed = new Feed();
            new RssParser(inputStream).parse(feed);
            feed.setChannelLink(link);
            feedRepository.save(feed);
        }
    }
}
