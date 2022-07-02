package ru.trett.rss.core;

import static org.junit.Assert.assertEquals;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.test.jdbc.JdbcTestUtils;

import ru.trett.rss.models.Channel;

import java.util.Collections;

import javax.sql.DataSource;

public class ChannelServiceSpecs {

    private JdbcTemplate jdbcTemplate;
    private ChannelService channelService;
    private FeedService feedService;

    @Before
    public void init() {
        DataSource dataSource =
                new EmbeddedDatabaseBuilder()
                        .setType(EmbeddedDatabaseType.H2)
                        .addScript("classpath:schema.sql")
                        .addScript("classpath:channel_test_data.sql")
                        .build();
        jdbcTemplate = new JdbcTemplate(dataSource);
        channelService = new ChannelService(jdbcTemplate);
        feedService = new FeedService(jdbcTemplate);
    }

    @After
    public void tearDown() {
        JdbcTestUtils.dropTables(jdbcTemplate, "feed_item", "channel", "user");
    }

    @Test
    public void testSaveChannel() {
        var channel = new Channel();
        channel.channelLink = "http://test.link";
        channel.link = "http://channel.link";
        channel.title = "test_title";
        channel.user = new UserService(jdbcTemplate).getUser("123").get();
        assertEquals(1, channelService.save(channel));
    }

    @Test
    public void testDeleteChannel() {
        feedService.deleteFeedsByChannel("123", 1);
        assertEquals(1, channelService.delete(1));
        assertEquals(Collections.emptyList(), channelService.findByUser("123"));
    }

    @Test
    public void testFindByUser() {
        assertEquals(1, channelService.findByUser("123").size());
    }
}
