package ru.trett.rss.models;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Set;

import javax.persistence.*;
import javax.validation.constraints.NotEmpty;

@Entity
public class Channel {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;

    @NotEmpty private String channelLink;
    @NotEmpty private String title;
    @NotEmpty private String link;

    @OneToMany(mappedBy = "channel", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<FeedItem> feedItems;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnore
    private User user;

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
