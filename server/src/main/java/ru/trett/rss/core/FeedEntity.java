package ru.trett.rss.core;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

public class FeedEntity {

    public long id;

    public String title;

    public String channelTitle;

    public String description;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
    public LocalDateTime pubDate;

    public String link;

    public boolean read;
}
