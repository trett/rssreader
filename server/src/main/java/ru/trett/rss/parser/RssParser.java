package ru.trett.rss.parser;

import com.rometools.rome.feed.synd.SyndContent;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.io.FeedException;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import ru.trett.rss.models.Channel;
import ru.trett.rss.models.Feed;

import java.io.IOException;
import java.io.InputStream;
import java.text.MessageFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class RssParser {

    private static final ZoneId ZONE_ID = ZoneId.systemDefault();
    private static final Logger LOG = LoggerFactory.getLogger(RssParser.class);

    public Channel parse(InputStream stream) throws IOException, FeedException {
        var input = new SyndFeedInput();
        var xmlReader = new XmlReader(stream);
        var syndFeed = input.build(xmlReader);
        var title = syndFeed.getTitle();
        LOG.info(
                MessageFormat.format(
                        "Parsing the channel: title ''{0}'', type ''{1}''",
                        title, syndFeed.getFeedType()));
        var channel = new Channel();
        channel.title = title;
        channel.link = syndFeed.getLink();
        var entries = syndFeed.getEntries();
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
        LOG.info("End of parsing the channel");
        return channel;
    }

    private String extractDescription(SyndEntry entry) {
        var description = Optional.ofNullable(entry.getDescription());
        if (description.isEmpty()) {
            var contents = entry.getContents();
            if (contents != null && !contents.isEmpty()) {
                description = Optional.of(contents.get(0));
            }
        }
        return description.map(SyndContent::getValue).orElse("");
    }

    private LocalDateTime extractDate(SyndEntry entry) {
        var date = Optional.ofNullable(entry.getPublishedDate()).orElse(entry.getUpdatedDate());
        if (date == null) {
            throw new RuntimeException("Date must not be empty! Feed: " + entry.getUri());
        }
        return date.toInstant().atZone(ZONE_ID).toLocalDateTime();
    }
}
