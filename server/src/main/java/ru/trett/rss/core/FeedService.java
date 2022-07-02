package ru.trett.rss.core;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import ru.trett.rss.models.Feed;

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
                    + " fi.read as read FROM public.channels c"
                    + " JOIN public.feeds fi ON fi.channel_id=c.id"
                    + " WHERE c.user_principal_name=?";

    private static final String NOT_READ_CONDITION = " AND NOT fi.read";

    private static final String ORDER_CLAUSE = " ORDER BY fi.pub_date DESC";

    public List<Feed> getItemsByUserName(String userName, boolean hideRead) {
        return jdbcTemplate.query(
                GET_ALL_FEEDS + (hideRead ? NOT_READ_CONDITION : "") + ORDER_CLAUSE,
                new FeedEntityRowMapper(),
                userName);
    }

    public List<Feed> getItemsByUserNameAndChannelId(
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

    private static final String DELETE_FEEDS = "DELETE FROM public.feeds fi where fi.id=?";

    public int deleteOldFeeds(String userName, int deleteAfter) {
        var since = LocalDateTime.now().minusDays(deleteAfter);
        var itemsToDelete =
                getItemsByUserName(userName, false).stream()
                        .filter(feedItem -> feedItem.pubDate.isBefore(since))
                        .map(feed -> feed.id)
                        .collect(Collectors.toList());
        batchDeleteFeeds(itemsToDelete);
        return itemsToDelete.size();
    }

    public int deleteFeedsByChannel(String userName, long channelId) {
        var itemsToDelete =
                getItemsByUserNameAndChannelId(userName, channelId, false).stream()
                        .map(feed -> feed.id)
                        .collect(Collectors.toList());
        batchDeleteFeeds(itemsToDelete);
        return itemsToDelete.size();
    }

    private static final String MARK_AS_READ = "UPDATE public.feeds SET read=true WHERE id=?";

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

    public int saveAll(List<Feed> feedItems, long channelId) {
        var res = updateFeeds(feedItems, channelId);
        var toInsert =
                IntStream.range(0, res.length)
                        .filter(idx -> res[idx] == 0)
                        .mapToObj(feedItems::get)
                        .collect(Collectors.toList());
        insertFeeds(toInsert, channelId);
        return toInsert.size();
    }

    private static final String UPDATE_FEED =
            "UPDATE public.feeds AS fi SET title=?, link=?, pub_date=?, description=?"
                    + " WHERE fi.channel_id=? AND fi.guid=?";

    private int[] updateFeeds(List<Feed> feedItems, long channelId) {
        return jdbcTemplate.batchUpdate(
                UPDATE_FEED,
                new BatchPreparedStatementSetter() {

                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        var feed = feedItems.get(i);
                        int idx = 0;
                        ps.setString(++idx, feed.title);
                        ps.setString(++idx, feed.link);
                        ps.setTimestamp(++idx, Timestamp.valueOf(feed.pubDate));
                        ps.setString(++idx, feed.description);
                        ps.setLong(++idx, channelId);
                        ps.setString(++idx, feed.guid);
                    }

                    public int getBatchSize() {
                        return feedItems.size();
                    }
                });
    }

    private static final String INSERT_FEED =
            "INSERT INTO public.feeds(guid, title, link, pub_date, description, read,"
                    + " channel_id) VALUES(?,?,?,?,?,?,?)";

    private int[] insertFeeds(List<Feed> feedItems, long channelId) {
        return jdbcTemplate.batchUpdate(
                INSERT_FEED,
                new BatchPreparedStatementSetter() {

                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        var feed = feedItems.get(i);
                        int idx = 0;
                        ps.setString(++idx, feed.guid);
                        ps.setString(++idx, feed.title);
                        ps.setString(++idx, feed.link);
                        ps.setTimestamp(++idx, Timestamp.valueOf(feed.pubDate));
                        ps.setString(++idx, feed.description);
                        ps.setBoolean(++idx, feed.read);
                        ps.setLong(++idx, channelId);
                    }

                    public int getBatchSize() {
                        return feedItems.size();
                    }
                });
    }

    private void batchDeleteFeeds(List<Long> itemsToDelete) {
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
    }

    private static class FeedEntityRowMapper implements RowMapper<Feed> {

        @Override
        public Feed mapRow(ResultSet rs, int rowNum) throws SQLException {
            var feedEntity = new Feed();
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
