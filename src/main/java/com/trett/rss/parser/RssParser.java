package com.trett.rss.parser;

import com.trett.rss.models.Channel;
import com.trett.rss.models.FeedItem;
import org.springframework.util.StringUtils;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class RssParser {

    private InputStream stream;

    public RssParser(InputStream stream) {
        this.stream = stream;
    }

    public Channel parse() throws XMLStreamException {
        Channel channel = new Channel();
        FeedItem feedItem = null;
        Set<FeedItem> feedItems = new LinkedHashSet<>();
        XMLInputFactory factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.IS_COALESCING, true);
        boolean itemStart = false;
        try {
            XMLEventReader xmlReader = factory.createXMLEventReader(stream);
            while (xmlReader.hasNext()) {
                XMLEvent event = xmlReader.nextEvent();
                if (event.isStartElement()) {
                    StartElement startElement = event.asStartElement();
                    QName name = startElement.getName();
                    // skip all non default namespaces
                    if (!StringUtils.isEmpty(name.getPrefix())) {
                        continue;
                    }
                    String localPart = name.getLocalPart();
                    if (!itemStart) {
                        switch (localPart) {
                            case "title":
                                channel.setTitle(getValueFromReader(xmlReader));
                                break;
                            case "link":
                                channel.setLink(getValueFromReader(xmlReader));
                                break;
                            case "item":
                                itemStart = true;
                                feedItem = new FeedItem();
                                feedItem.setChannel(channel);
                                break;
                        }
                    } else {
                        switch (localPart) {
                            case "title":
                                feedItem.setTitle(getValueFromReader(xmlReader));
                                break;
                            case "link":
                                feedItem.setLink(getValueFromReader(xmlReader));
                                break;
                            case "guid":
                                feedItem.setGuid(getValueFromReader(xmlReader));
                            case "pubDate":
                                String date = getValueFromReader(xmlReader);
                                if (date != null) {
                                    feedItem.setPubDate(LocalDateTime.parse(date,
                                            DateTimeFormatter.RFC_1123_DATE_TIME));
                                }
                                break;
                            case "description":
                                feedItem.setDescription(getValueFromReader(xmlReader));
                                break;
                        }
                    }
                }
                if (event.isEndElement()) {
                    EndElement endElement = event.asEndElement();
                    if (endElement.getName().getLocalPart().equals("item")) {
                        if (feedItem != null) {
                            feedItems.add(feedItem);
                        }
                        itemStart = false;
                    }
                }
            }
            channel.setFeedItems(feedItems);
            return channel;
        } catch (Exception e) {
            throw new RuntimeException("Can't parse feed", e);
        }
    }

    public Set<FeedItem> geeNewFeeds(Channel channel) throws XMLStreamException {
        return parse()
                .getFeedItems()
                .stream()
                .filter(item -> !channel.getFeedItems().contains(item))
                .peek(feedItem -> feedItem.setChannel(channel))
                .collect(Collectors.toSet());
    }

    private String getValueFromReader(XMLEventReader reader) throws XMLStreamException {
        XMLEvent event = reader.nextEvent();
        if (event.isCharacters()) {
            return event.asCharacters().getData().replaceAll("</?script>", "");
        }
        return null;
    }
}
