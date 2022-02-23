package ru.rss.trett;

import org.junit.Test;
import ru.trett.rss.models.Channel;
import ru.trett.rss.parser.RssParser;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.Assert.assertEquals;

public class RssApplicationTests {

    @Test
    public void testParserRSS() throws IOException {
        try (InputStream is = getClass().getResourceAsStream("/test.xml")) {
            Channel channel = new RssParser(is).parse();
            assertEquals("JUG.ru", channel.getTitle());
            assertEquals(10, channel.getFeedItems().size());
        }
    }

    @Test
    public void testParserAtom() throws IOException {
        try (InputStream is = getClass().getResourceAsStream("/blog.atom")) {
            Channel channel = new RssParser(is).parse();
            assertEquals("Spring", channel.getTitle());
            assertEquals(20, channel.getFeedItems().size());
        }
    }

    @Test
    public void testParserReddit() throws IOException {
        try (InputStream is = getClass().getResourceAsStream("/reddit.rss")) {
            Channel channel = new RssParser(is).parse();
            assertEquals("posts from java, WTF", channel.getTitle());
            assertEquals(25, channel.getFeedItems().size());
        }
    }
}

