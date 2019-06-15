package com.trett.rss.models;


import com.fasterxml.jackson.annotation.JsonIgnore;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

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

    @OneToMany(mappedBy = "channel", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<FeedItem> feedItems;

    @ManyToOne
    @JoinColumn
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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Channel channel = (Channel) o;
        return new EqualsBuilder()
                .append(getId(), channel.getId())
                .append(getTitle(), channel.getTitle())
                .append(getLink(), channel.getLink())
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37)
                .append(getId())
                .append(getTitle())
                .append(getLink())
                .toHashCode();
    }
}
