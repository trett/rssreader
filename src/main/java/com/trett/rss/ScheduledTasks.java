package com.trett.rss;

import com.trett.rss.dao.ChannelRepository;
import com.trett.rss.dao.FeedItemRepository;
import com.trett.rss.models.Channel;
import com.trett.rss.models.FeedItem;
import com.trett.rss.parser.RssParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.text.MessageFormat;
import java.time.LocalDateTime;
import java.util.Set;

@Component
public class ScheduledTasks {

    private Logger logger = LoggerFactory.getLogger(ScheduledTasks.class);

    private ChannelRepository channelRepository;

    private FeedItemRepository feedItemRepository;

    @Autowired
    public ScheduledTasks(ChannelRepository channelRepository, FeedItemRepository feedItemRepository) {
        this.channelRepository = channelRepository;
        this.feedItemRepository = feedItemRepository;
    }

    /**
     * Update all channels every hour
     * @throws XMLStreamException
     * @throws IOException
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void updateFeeds() throws XMLStreamException, IOException {
        RestTemplate restTemplate = new RestTemplate();
        ClientHttpRequestFactory requestFactory = restTemplate.getRequestFactory();
        logger.info("Starting update feeds at " + LocalDateTime.now());
        for (Channel channel : channelRepository.findAll()) {
            ClientHttpRequest request = requestFactory
                    .createRequest(URI.create(channel.getChannelLink()), HttpMethod.GET);
            ClientHttpResponse execute = request.execute();
            try (InputStream inputStream = execute.getBody()) {
                Set<FeedItem> feedItems = new RssParser(inputStream).geeNewFeeds(channel);
                logger.info(MessageFormat.format("{0} items was update for {1} channel",
                        feedItems.size(), channel.getTitle()));
                feedItemRepository.saveAll(feedItems);
            }
        }
        logger.info("End of updating feeds");
    }
}
