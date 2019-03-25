package com.trett.rss.models;


import javax.persistence.*;
import javax.validation.constraints.NotEmpty;
import java.util.Set;

/**
 * RSS feed with items
 */
@Entity
public class Channel {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;

    @NotEmpty
    private String channelLink;

    @NotEmpty
    private String title;

    @NotEmpty
    private String link;

    @OneToMany(mappedBy = "channel", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private Set<FeedItem> feedItems;

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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Channel channel = (Channel) o;
        return id == channel.id && title.equals(channel.title) && link.equals(channel.link);
    }

    @Override
    public int hashCode() {
        int result = (int) (id ^ (id >>> 32));
        result = 31 * result + title.hashCode();
        result = 31 * result + link.hashCode();
        return result;
    }
}
