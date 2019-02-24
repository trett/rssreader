package com.trett.rss.models;


import javax.persistence.*;
import java.util.Set;

@Entity
public class Feed {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;

    private String title;

    private String link;

    @OneToMany(mappedBy = "feed", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private Set<FeedItem> feedItems;

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
}
