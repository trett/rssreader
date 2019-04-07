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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.Set;

public class RssParser {

    private InputStream stream;

    public RssParser(InputStream stream) {
        this.stream = stream;
    }

    public Channel parse(Channel channel) throws XMLStreamException {
        FeedItem feedItem = null;
        Set<FeedItem> feedItems = new LinkedHashSet<>();
        XMLInputFactory factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.IS_COALESCING, true);
        XMLEventReader xmlReader = factory.createXMLEventReader(stream);
        boolean itemStart = false;
        try {
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
                                channel.setTitle(getValueFromEvent(xmlReader.nextEvent()));
                                break;
                            case "link":
                                channel.setLink(getValueFromEvent(xmlReader.nextEvent()));
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
                                feedItem.setTitle(getValueFromEvent(xmlReader.nextEvent()));
                                break;
                            case "link":
                                feedItem.setLink(getValueFromEvent(xmlReader.nextEvent()));
                                break;
                            case "pubDate":
                                String date = getValueFromEvent(xmlReader.nextEvent());
                                if (date != null) {
                                    feedItem.setPubDate(LocalDate.parse(date, DateTimeFormatter.RFC_1123_DATE_TIME));
                                }
                                break;
                            case "description":
                                feedItem.setDescription(getValueFromEvent(xmlReader.nextEvent()));
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
            throw new RuntimeException("Can't parse feed");
        }
    }

    private String getValueFromEvent(XMLEvent event) {
        if (event.isCharacters()) {
            return event.asCharacters().getData();
        }
        return null;
    }
}
