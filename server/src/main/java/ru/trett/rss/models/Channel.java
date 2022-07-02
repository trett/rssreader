package ru.trett.rss.models;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Set;

public class Channel {

    private long id;

    private String channelLink;
    private String title;
    private String link;

    private Set<FeedItem> feedItems;

    @JsonIgnore private User user;

    public String getChannelLink() {
        return channelLink;
    }

    public void setChannelLink(String channelLink) {
        this.channelLink = channelLink;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getLink() {
        return link;
    }

    public void setLink(String link) {
        this.link = link;
    }

    public Set<FeedItem> getFeedItems() {
        return feedItems;
    }

    public void setFeedItems(Set<FeedItem> feedItems) {
        this.feedItems = feedItems;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }
}
