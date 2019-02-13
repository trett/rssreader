package com.trett.rss.controllers;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.impl.IndexedListSerializer;
import com.trett.rss.models.Feed;
import com.trett.rss.models.Record;
import com.trett.rss.parser.RssParser;
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
import java.util.ArrayList;
import java.util.List;

@RestController
public class News {

    @GetMapping(path = "/feed", produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    @JsonSerialize(using = IndexedListSerializer.class)
    public List<Record> getFeeds() throws IOException, XMLStreamException, URISyntaxException {
        RestTemplate restTemplate = new RestTemplate();
        ClientHttpRequest request = restTemplate.getRequestFactory()
                .createRequest(new URI("https://www.flenov.info/site/rss"), HttpMethod.GET);
        ClientHttpResponse execute = request.execute();
        List<Record> news = new ArrayList<>();
        try (InputStream inputStream = execute.getBody()) {
            Feed feed = new RssParser(inputStream).parse();
            news.add(new Record(feed.getTitle(), feed.getLink()));
        }

        return news;
    }
}