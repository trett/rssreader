package com.trett.rss.controllers;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.impl.IndexedListSerializer;
import com.trett.rss.models.Record;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class News {

    @GetMapping(path="/news", produces=MediaType.APPLICATION_JSON_UTF8_VALUE)
    @JsonSerialize(using = IndexedListSerializer.class)
    public List<Record> index() throws JsonProcessingException {
        List<Record> news = new ArrayList<>();
        news.add(new Record("n1", "l1"));
        news.add(new Record("n2", "l3"));
        return news;
    }
}