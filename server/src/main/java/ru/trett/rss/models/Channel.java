package ru.trett.rss.models;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public class Channel {

    public long id;

    /** RSS link */
    @NotEmpty public String channelLink;

    @NotEmpty public String title;

    /** Unique link for channel */
    @NotEmpty public String link;

    public List<Feed> feedItems = List.of();
}
