package com.trett.rss.models;

import com.fasterxml.jackson.annotation.JsonIgnore;

import javax.persistence.*;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Size;

/**
 * RSS channel item
 */
@Entity
public class FeedItem {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;

    @NotEmpty
    private String title;

    @NotEmpty
    private String link;

    @Size(max = 10000)
    @Column(length = 10000)
    private String description;

    @ManyToOne
    @JoinColumn
    @JsonIgnore
    private Channel channel;

    private boolean read;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Channel getChannel() {
        return channel;
    }

    public void setChannel(Channel channel) {
        this.channel = channel;
    }

    public boolean isRead() {
        return read;
    }

    public void setRead(boolean read) {
        this.read = read;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        FeedItem feedItem = (FeedItem) o;
        return title.equals(feedItem.title) &&
                link.equals(feedItem.link) &&
                (description != null ? description.equals(feedItem.description) : feedItem.description == null) &&
                channel.equals(feedItem.channel);
    }

    @Override
    public int hashCode() {
        int result = title.hashCode();
        result = 31 * result + link.hashCode();
        result = 31 * result + (description != null ? description.hashCode() : 0);
        result = 31 * result + channel.hashCode();
        return result;
    }
}
