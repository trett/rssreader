package com.trett.rss;

import com.trett.rss.models.Channel;
import com.trett.rss.parser.RssParser;
import org.junit.Test;

import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.Assert.assertEquals;

public class RssApplicationTests {

    @Test
    public void testParser() throws IOException, XMLStreamException {
        try (InputStream is = getClass().getResourceAsStream("/test.xml")) {
            Channel channel = new RssParser(is).parse();
            assertEquals("JUG.ru", channel.getTitle());
            assertEquals(10, channel.getFeedItems().size());
        }
    }
}

