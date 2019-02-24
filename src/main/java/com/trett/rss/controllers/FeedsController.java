package com.trett.rss.controllers;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.impl.IndexedListSerializer;
import com.trett.rss.dao.FeedRepository;
import com.trett.rss.models.Feed;
import com.trett.rss.parser.RssParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashSet;
import java.util.Set;

@RestController
public class FeedsController {

    private final FeedRepository feedRepository;

    @Autowired
    public FeedsController(FeedRepository feedRepository) {
        this.feedRepository = feedRepository;
    }

    @GetMapping(path = "/refresh")
    @JsonSerialize(using = IndexedListSerializer.class)
    public void refresh() throws IOException, XMLStreamException, URISyntaxException {
        RestTemplate restTemplate = new RestTemplate();
        ClientHttpRequest request = restTemplate.getRequestFactory()
                .createRequest(new URI("https://www.flenov.info/site/rss"), HttpMethod.GET);
        ClientHttpResponse execute = request.execute();
        try (InputStream inputStream = execute.getBody()) {
            Feed feed = new RssParser(inputStream).parse();
            feedRepository.save(feed);
        }
    }

    @GetMapping(path = "/news", produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    @JsonSerialize(using = IndexedListSerializer.class)
    public Set<Feed> getNews() throws IOException, XMLStreamException, URISyntaxException {
        Set<Feed> items = new LinkedHashSet<>();
        Iterable<Feed> all = feedRepository.findAll();
        all.forEach(items::add);
        return items;
    }
}