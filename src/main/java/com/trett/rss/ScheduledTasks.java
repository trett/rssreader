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
import java.util.Iterator;
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
    public void updateFeeds() throws XMLStreamException, IOException {
        ClientHttpRequestFactory requestFactory = restTemplate.getRequestFactory();
        logger.info("Starting update feeds at " + LocalDateTime.now());
        Iterator<User> userIterator = userRepository.findAll().iterator();
        while (userIterator.hasNext()) {
            User user = userIterator.next();
            logger.info("Starting update feeds for user: " + user.getPrincipalName());
            Iterator<Channel> channelIterator = channelRepository.findByUserEager(user).iterator();
            while (channelIterator.hasNext()) {
                Channel channel = channelIterator.next();
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
        Iterable<User> users = userRepository.findAll();
        users.forEach(user -> {
            logger.info("delete feeds for user: " + user.getPrincipalName());
            feedItemRepository.deleteFeedsOlderThan(LocalDateTime.now().minusDays(user.getSettings().getDeleteAfter()));
        });
        logger.info("End of delete old feeds");
    }
}
