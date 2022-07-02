package ru.trett.rss.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.test.jdbc.JdbcTestUtils;

import ru.trett.rss.models.Feed;

import java.time.LocalDateTime;
import java.util.List;

import javax.sql.DataSource;

public class FeedServiceSpecs {

    private JdbcTemplate jdbcTemplate;
    private FeedService feedService;

    @Before
    public void init() {
        DataSource dataSource =
                new EmbeddedDatabaseBuilder()
                        .setType(EmbeddedDatabaseType.H2)
                        .addScript("classpath:schema.sql")
                        .addScript("classpath:feeds_test_data.sql")
                        .build();
        jdbcTemplate = new JdbcTemplate(dataSource);
        feedService = new FeedService(jdbcTemplate);
    }

    @After
    public void tearDown() {
        JdbcTestUtils.dropTables(jdbcTemplate, "feeds", "channels", "users");
    }

    @Test
    public void testGetAllFeeds() {
        var feeds = feedService.getItemsByUserName("123", false);
        assertEquals(2, feeds.size());
    }

    @Test
    public void testGetUnreadItems() {
        var feeds = feedService.getItemsByUserName("123", true);
        assertEquals(1, feeds.size());
    }

    @Test
    public void testSaveFeed() {
        feedService.saveAll(List.of(createFeed()), 1);
        var feeds = feedService.getItemsByUserName("123", false);
        assertEquals(3, feeds.size());
    }

    @Test
    public void testUpdateFeed() {
        var feed = createFeed();
        feed.guid = "guid234";
        feedService.saveAll(List.of(feed), 1);
        var feeds = feedService.getItemsByUserName("123", false);
        assertEquals(2, feeds.size());
    }

    @Test
    public void testDeleteFeeds() {
        var feed = createFeed();
        feed.pubDate = LocalDateTime.now().minusDays(1);
        feedService.saveAll(List.of(feed), 1);
        var deleted = feedService.deleteOldFeeds("123", 7);
        assertEquals(2, deleted);
        var feeds = feedService.getItemsByUserName("123", false);
        assertEquals(1, feeds.size());
    }

    @Test
    public void testMarkAsRead() {
        var feed = createFeed();
        feed.read = true;
        feedService.saveAll(List.of(feed), 1);
        var feeds = feedService.getItemsByUserName("123", false);
        assertTrue(feeds.get(0).read);
    }

    private Feed createFeed() {
        var feed = new Feed();
        feed.guid = "guid345";
        feed.title = "inserted_item";
        feed.link = "http://testLink";
        feed.pubDate = LocalDateTime.now();
        feed.description = "description";
        return feed;
    }
}
