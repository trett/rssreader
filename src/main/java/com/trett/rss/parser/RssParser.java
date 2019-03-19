package com.trett.rss.parser;

import com.trett.rss.models.Feed;
import com.trett.rss.models.FeedItem;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import java.io.InputStream;
import java.util.LinkedHashSet;
import java.util.Set;

public class RssParser {

    private InputStream stream;

    public RssParser(InputStream stream) {

        this.stream = stream;
    }

    public Feed parse(Feed feed) throws XMLStreamException {
        FeedItem feedItem = null;
        Set<FeedItem> feedItems = new LinkedHashSet<>();
        XMLInputFactory factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.IS_COALESCING, true);
        XMLEventReader xmlReader = factory.createXMLEventReader(stream);
        boolean itemStart = false;
        while (xmlReader.hasNext()) {
            XMLEvent event = xmlReader.nextEvent();
            if (event.isStartElement()) {
                StartElement startElement = event.asStartElement();
                String qName = startElement.getName().getLocalPart();
                if (!itemStart) {
                    switch (qName) {
                        case "title":
                            event = xmlReader.nextEvent();
                            feed.setTitle(event.asCharacters().getData());
                            break;
                        case "link":
                            event = xmlReader.nextEvent();
                            feed.setLink(event.asCharacters().getData());
                            break;
                        case "item":
                            itemStart = true;
                            feedItem = new FeedItem();
                            feedItem.setFeed(feed);
                            break;
                    }
                } else {
                    switch (qName) {
                        case "title":
                            event = xmlReader.nextEvent();
                            feedItem.setTitle(event.asCharacters().getData());
                            break;
                        case "link":
                            event = xmlReader.nextEvent();
                            feedItem.setLink(event.asCharacters().getData());
                            break;
                        case "description":
                            event = xmlReader.nextEvent();
                            feedItem.setDescription(event.asCharacters().getData());
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
        feed.setFeedItems(feedItems);
        return feed;
    }
}
