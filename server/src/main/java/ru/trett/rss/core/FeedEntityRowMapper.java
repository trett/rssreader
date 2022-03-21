package ru.trett.rss.core;

import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class FeedEntityRowMapper implements RowMapper<FeedEntity> {

    @Override
    public FeedEntity mapRow(ResultSet rs, int rowNum) throws SQLException {
        FeedEntity feedEntity = new FeedEntity();
        feedEntity.id = rs.getLong("feed_id");
        feedEntity.channelTitle = rs.getString("channel_title");
        feedEntity.title = rs.getString("feed_title");
        feedEntity.description = rs.getString("feed_description");
        feedEntity.pubDate = rs.getTimestamp("feed_date").toLocalDateTime();
        feedEntity.link = rs.getString("feed_link");
        feedEntity.read = rs.getBoolean("read");
        return feedEntity;
    }
}
