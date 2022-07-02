package ru.trett.rss.models;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.List;

import javax.validation.constraints.NotEmpty;

public class Channel {

    private long id;
    @NotEmpty public String channelLink;
    @NotEmpty public String title;
    @NotEmpty public String link;
    public List<Feed> feedItems;
    @JsonIgnore public User user;

    public long getId() {
        return id;
    }
}
