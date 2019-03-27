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
                            event = xmlReader.nextEvent();
                            channel.setTitle(event.asCharacters().getData());
                            break;
                        case "link":
                            event = xmlReader.nextEvent();
                            channel.setLink(event.asCharacters().getData());
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
        channel.setFeedItems(feedItems);
        return channel;
    }
}
