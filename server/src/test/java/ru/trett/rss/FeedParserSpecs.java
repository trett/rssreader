package ru.trett.rss;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import ru.trett.rss.models.Channel;
import ru.trett.rss.parser.RssParser;

import java.io.IOException;
import java.io.InputStream;

public class FeedParserSpecs {

    @Test
    public void testParserRSS() throws IOException {
        try (InputStream is = getClass().getResourceAsStream("/test.xml")) {
            Channel channel = new RssParser(is).parse();
            assertEquals("JUG.ru", channel.title);
            assertEquals(10, channel.feedItems.size());
        }
    }

    @Test
    public void testParserAtom() throws IOException {
        try (InputStream is = getClass().getResourceAsStream("/blog.atom")) {
            Channel channel = new RssParser(is).parse();
            assertEquals("Spring", channel.title);
            assertEquals(20, channel.feedItems.size());
        }
    }

    @Test
    public void testParserReddit() throws IOException {
        try (InputStream is = getClass().getResourceAsStream("/reddit.rss")) {
            Channel channel = new RssParser(is).parse();
            assertEquals("posts from java, WTF", channel.title);
            assertEquals(25, channel.feedItems.size());
        }
    }
}
