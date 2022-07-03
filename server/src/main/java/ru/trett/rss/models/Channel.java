package ru.trett.rss.models;

import java.util.List;

import javax.validation.constraints.NotEmpty;

public class Channel {

    public long id;
    /** RSS link */
    @NotEmpty public String channelLink;

    @NotEmpty public String title;
    /** Unique link for channel */
    @NotEmpty public String link;

    public List<Feed> feedItems;
}
