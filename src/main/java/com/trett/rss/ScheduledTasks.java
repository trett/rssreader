package com.trett.rss;

import com.trett.rss.dao.ChannelRepository;
import com.trett.rss.dao.FeedItemRepository;
import com.trett.rss.dao.UserRepository;
import com.trett.rss.models.Channel;
import com.trett.rss.models.FeedItem;
import com.trett.rss.models.User;
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

    private RestTemplate restTemplate;

    private UserRepository userRepository;

    @Autowired
    public ScheduledTasks(ChannelRepository channelRepository, FeedItemRepository feedItemRepository,
                          UserRepository userRepository, RestTemplate restTemplate) {
        this.channelRepository = channelRepository;
        this.feedItemRepository = feedItemRepository;
        this.userRepository = userRepository;
        this.restTemplate = restTemplate;
    }

    /**
     * Update all channels every hour
     *
     * @throws XMLStreamException
     * @throws IOException
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void updateFeeds() {
        ClientHttpRequestFactory requestFactory = restTemplate.getRequestFactory();
        logger.info("Starting update feeds at " + LocalDateTime.now());
        for (User user : userRepository.findAll()) {
            logger.info("Starting update feeds for user: " + user.getPrincipalName());
            for (Channel channel : channelRepository.findByUserEager(user)) {
                try {
                    ClientHttpRequest request = requestFactory.createRequest(URI.create(channel.getChannelLink()),
                            HttpMethod.GET);
                    ClientHttpResponse execute = request.execute();
                    try (InputStream inputStream = execute.getBody()) {
                        Set<FeedItem> feedItems = new RssParser(inputStream).getNewFeeds(channel,
                                user.getSettings().getDeleteAfter());
                        logger.info(MessageFormat.format("{0} items was update for ''{1}''", feedItems.size(),
                                channel.getTitle()));
                        feedItemRepository.saveAll(feedItems);
                    }
                } catch (Exception e) {
                    // logging and update next channel
                    logger.info("Error occured during parse channel: " + channel.getTitle(), e);
                }
            }
        }
        logger.info("End of updating feeds");
    }

    /**
     * Delete old feeds for all users every day at 00:00
     */
    @Scheduled(cron = "0 0 0 * * ?")
    public void deleteFeeds() {
        logger.info("Starting delete old feeds");
        userRepository.findAll().forEach(user -> {
            logger.info("Delete feeds for user: " + user.getPrincipalName());
            channelRepository.findByUserEager(user)
                    .forEach(channel ->
                            channel.getFeedItems()
                                    .stream()
                                    .filter(feedItem ->
                                            feedItem.getPubDate()
                                                    .isBefore(LocalDateTime.now()
                                                            .minusDays(user.getSettings().getDeleteAfter())))
                                    .peek(feedItem -> logger.info("Deleting feed: " + feedItem.getGuid()))
                                    .forEach(feedItem -> feedItemRepository.delete(feedItem)));
        });
        logger.info("End of delete old feeds");
    }
}
