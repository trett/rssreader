package ru.trett.rss.core;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.PreparedStatement;
import java.sql.SQLException;
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
                    + " fi.read as read FROM channel c"
                    + " JOIN feed_item fi ON fi.channel_id = c.id WHERE"
                    + " c.user_principal_name = ?";

    private static final String NOT_READ_CONDITION = " AND NOT fi.read";

    private static final String ORDER_CLAUSE = " ORDER BY fi.pub_date DESC";

    public List<FeedEntity> getItemsByUserName(String userName, boolean hideRead) {
        return jdbcTemplate.query(
                GET_ALL_FEEDS + (hideRead ? NOT_READ_CONDITION : "") + ORDER_CLAUSE,
                new FeedEntityRowMapper(),
                userName);
    }

    public List<FeedEntity> getItemsByUserNameAndChannelId(
            String userName, long channelId, boolean hideRead) {
        var sql = GET_ALL_FEEDS + " AND c.id = ?";
        if (hideRead) {
            return jdbcTemplate.query(
                    sql + NOT_READ_CONDITION + ORDER_CLAUSE,
                    new FeedEntityRowMapper(),
                    userName,
                    channelId);
        } else {
            return jdbcTemplate.query(
                    sql + ORDER_CLAUSE, new FeedEntityRowMapper(), userName, channelId);
        }
    }

    private static final String MARK_AS_READ = "UPDATE feed_item SET read = true WHERE id = ?";

    public void markAsRead(Long[] ids) {
        jdbcTemplate.batchUpdate(
                MARK_AS_READ,
                new BatchPreparedStatementSetter() {

                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        ps.setLong(1, ids[i]);
                    }

                    public int getBatchSize() {
                        return ids.length;
                    }
                });
    }
}
