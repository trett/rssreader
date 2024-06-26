package ru.trett.rss.models;

import com.fasterxml.jackson.annotation.JsonFormat;

import jakarta.validation.constraints.NotEmpty;

import java.time.LocalDateTime;

public class Feed {

    public long id;
    @NotEmpty public String title;
    @NotEmpty public String guid;
    @NotEmpty public String channelTitle;
    public String description;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
    public LocalDateTime pubDate;

    public String link;
    public boolean read;
}
