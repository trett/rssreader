package ru.trett.rss.core;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import ru.trett.rss.models.FeedItem;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
                    + " JOIN feed_item fi ON fi.channel_id=c.id"
                    + " WHERE c.user_principal_name=?";

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

    private static final String DELETE_FEEDS = "DELETE FROM feed_item fi where fi.id=?";

    public void delete(long id) {
        jdbcTemplate.update(DELETE_FEEDS, id);
    }

    public int deleteOldFeeds(String userName, int deleteAfter) {
        var since = LocalDateTime.now().minusDays(deleteAfter);
        var itemsToDelete =
                getItemsByUserName(userName, false).stream()
                        .filter(feedItem -> feedItem.pubDate.isBefore(since))
                        .map(feedItem -> feedItem.id)
                        .collect(Collectors.toList());

        jdbcTemplate.batchUpdate(
                DELETE_FEEDS,
                new BatchPreparedStatementSetter() {

                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        ps.setLong(1, itemsToDelete.get(i));
                    }

                    public int getBatchSize() {
                        return itemsToDelete.size();
                    }
                });
        return itemsToDelete.size();
    }

    private static final String MARK_AS_READ = "UPDATE feed_item SET read=true WHERE id=?";

    public void markAsRead(List<Long> ids, String userName) {
        var itemsToUpdate =
                getItemsByUserName(userName, false).stream()
                        .filter(feedItem -> ids.contains(feedItem.id))
                        .collect(Collectors.toList());
        jdbcTemplate.batchUpdate(
                MARK_AS_READ,
                new BatchPreparedStatementSetter() {

                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        ps.setLong(1, itemsToUpdate.get(i).id);
                    }

                    public int getBatchSize() {
                        return itemsToUpdate.size();
                    }
                });
    }

    public int saveAll(List<FeedItem> feedItems, long channelId) {
        var res = updateFeeds(feedItems, channelId);
        var toInsert =
                IntStream.range(0, res.length)
                        .filter(idx -> res[idx] == 0)
                        .mapToObj(idx -> feedItems.get(idx))
                        .collect(Collectors.toList());
        insertFeeds(toInsert, channelId);
        return toInsert.size();
    }

    private static final String UPDATE_FEED =
            "UPDATE feed_item AS fi SET title=?, link=?, pub_date=?, description=?"
                    + " WHERE fi.channel_id=? AND fi.guid=?";

    private int[] updateFeeds(List<FeedItem> feedItems, long channelId) {
        return jdbcTemplate.batchUpdate(
                UPDATE_FEED,
                new BatchPreparedStatementSetter() {

                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        var feed = feedItems.get(i);
                        int idx = 0;
                        ps.setString(++idx, feed.getTitle());
                        ps.setString(++idx, feed.getLink());
                        ps.setTimestamp(++idx, Timestamp.valueOf(feed.getPubDate()));
                        ps.setString(++idx, feed.getDescription());
                        ps.setLong(++idx, channelId);
                        ps.setString(++idx, feed.getGuid());
                    }

                    public int getBatchSize() {
                        return feedItems.size();
                    }
                });
    }

    private static final String INSERT_FEED =
            "INSERT INTO feed_item(id, guid, title, link, pub_date, description, read, channel_id)"
                    + " VALUES(?,?,?,?,?,?,?,?)";

    private int[] insertFeeds(List<FeedItem> feedItems, long channelId) {
        return jdbcTemplate.batchUpdate(
                INSERT_FEED,
                new BatchPreparedStatementSetter() {

                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        var feed = feedItems.get(i);
                        var id =
                                jdbcTemplate.queryForObject(
                                        "SELECT NEXTVAL('hibernate_sequence')", Long.class);
                        int idx = 0;
                        ps.setLong(++idx, id);
                        ps.setString(++idx, feed.getGuid());
                        ps.setString(++idx, feed.getTitle());
                        ps.setString(++idx, feed.getLink());
                        ps.setTimestamp(++idx, Timestamp.valueOf(feed.getPubDate()));
                        ps.setString(++idx, feed.getDescription());
                        ps.setBoolean(++idx, feed.isRead());
                        ps.setLong(++idx, channelId);
                    }

                    public int getBatchSize() {
                        return feedItems.size();
                    }
                });
    }

    private static class FeedEntityRowMapper implements RowMapper<FeedEntity> {

        @Override
        public FeedEntity mapRow(ResultSet rs, int rowNum) throws SQLException {
            var feedEntity = new FeedEntity();
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
}
