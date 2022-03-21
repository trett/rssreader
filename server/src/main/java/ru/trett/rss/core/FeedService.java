package ru.trett.rss.core;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FeedService {

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public FeedService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final String GET_ALL_FEEDS =
            "SELECT c.title as channel_title, fi.id as feed_id, fi.title as feed_title, fi.link as"
                + " feed_link, fi.pub_date as feed_date, fi.description as feed_description,"
                + " fi.read as read FROM channel c JOIN feed_item fi ON fi.channel_id = c.id WHERE"
                + " c.user_principal_name = ?";
    private static final String GET_UNREAD_FEEDS = GET_ALL_FEEDS + " AND NOT fi.read";

    public List<FeedEntity> getItemsByUserName(String userName, boolean hideRead) {
        return jdbcTemplate.query(
                (hideRead ? GET_UNREAD_FEEDS : GET_ALL_FEEDS) + " ORDER BY fi.pub_date DESC",
                new FeedEntityRowMapper(),
                userName);
    }
}
