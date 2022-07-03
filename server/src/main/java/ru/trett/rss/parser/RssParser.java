package ru.trett.rss.parser;

import com.rometools.rome.feed.synd.SyndContent;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.FeedException;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ru.trett.rss.models.Channel;
import ru.trett.rss.models.Feed;

import java.io.IOException;
import java.io.InputStream;
import java.text.MessageFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class RssParser {

    private static final ZoneId ZONE_ID = ZoneId.systemDefault();
    private static final Logger LOG = LoggerFactory.getLogger(RssParser.class);
    private final InputStream stream;

    public RssParser(InputStream stream) {
        this.stream = stream;
    }

    public Channel parse() throws IOException {
        try {
            SyndFeedInput input = new SyndFeedInput();
            try (XmlReader xmlReader = new XmlReader(stream)) {
                SyndFeed syndFeed = input.build(xmlReader);
                String title = syndFeed.getTitle();
                LOG.info(
                        MessageFormat.format(
                                "Parse channel: title ''{0}'', type ''{1}''",
                                title, syndFeed.getFeedType()));
                Channel channel = new Channel();
                channel.title = title;
                channel.link = syndFeed.getLink();
                List<SyndEntry> entries = syndFeed.getEntries();
                var feedItems =
                        entries.stream()
                                .map(
                                        entry -> {
                                            var feed = new Feed();
                                            feed.guid = entry.getUri();
                                            feed.description = extractDescription(entry);
                                            feed.title = entry.getTitle();
                                            feed.pubDate = extractDate(entry);
                                            feed.link = entry.getLink();
                                            return feed;
                                        })
                                .collect(Collectors.toList());
                channel.feedItems = feedItems;
                LOG.info("Parsed " + feedItems.size() + " items");
                LOG.info("End of parse channel");
                return channel;
            }
        } catch (FeedException e) {
            throw new RuntimeException("Can't parse feed", e);
        }
    }

    private String extractDescription(SyndEntry entry) {
        Optional<SyndContent> description = Optional.ofNullable(entry.getDescription());
        if (!description.isPresent()) {
            List<SyndContent> contents = entry.getContents();
            if (contents != null && !contents.isEmpty()) {
                description = Optional.of(contents.get(0));
            }
        }
        return description.map(SyndContent::getValue).orElse("");
    }

    private LocalDateTime extractDate(SyndEntry entry) {
        Date date = Optional.ofNullable(entry.getPublishedDate()).orElse(entry.getUpdatedDate());
        if (date == null) {
            throw new RuntimeException("Date must not be empty! Feed: " + entry.getUri());
        }
        return date.toInstant().atZone(ZONE_ID).toLocalDateTime();
    }
}
