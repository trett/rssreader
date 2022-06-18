package ru.trett.rss;

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

import ru.trett.rss.core.FeedService;
import ru.trett.rss.dao.ChannelRepository;
import ru.trett.rss.dao.UserRepository;
import ru.trett.rss.models.Channel;
import ru.trett.rss.models.User;
import ru.trett.rss.parser.RssParser;

import java.io.InputStream;
import java.net.URI;
import java.text.MessageFormat;
import java.time.LocalDateTime;
import java.util.stream.Collectors;

@Component
public class ScheduledTasks {

    private static final Logger LOG = LoggerFactory.getLogger(ScheduledTasks.class);
    private ChannelRepository channelRepository;
    private FeedService feedService;
    private RestTemplate restTemplate;
    private UserRepository userRepository;

    @Autowired
    public ScheduledTasks(
            ChannelRepository channelRepository,
            FeedService feedService,
            UserRepository userRepository,
            RestTemplate restTemplate) {
        this.channelRepository = channelRepository;
        this.feedService = feedService;
        this.userRepository = userRepository;
        this.restTemplate = restTemplate;
    }

    /**
     * Update all channels every hour
     *
     * @throws XMLStreamException
     * @throws IOException
     */
    @Scheduled(cron = "0 0/30 * * * ?")
    public void updateFeeds() {
        ClientHttpRequestFactory requestFactory = restTemplate.getRequestFactory();
        LOG.info("Starting of update feeds at " + LocalDateTime.now());
        for (User user : userRepository.findAll()) {
            LOG.info("Starting of update feeds for user: " + user.getPrincipalName());
            for (Channel channel : channelRepository.findByUser(user)) {
                try {
                    ClientHttpRequest request =
                            requestFactory.createRequest(
                                    URI.create(channel.getChannelLink()), HttpMethod.GET);
                    ClientHttpResponse execute = request.execute();
                    try (InputStream inputStream = execute.getBody()) {
                        var since =
                                LocalDateTime.now().minusDays(user.getSettings().getDeleteAfter());
                        var feeds =
                                new RssParser(inputStream)
                                        .parse().getFeedItems().stream()
                                                .filter(feed -> feed.getPubDate().isAfter(since))
                                                .collect(Collectors.toList());
                        int inserted = feedService.saveAll(feeds, channel.getId());
                        LOG.info(
                                MessageFormat.format(
                                        "{0} items was updated for ''{1}''",
                                        inserted, channel.getTitle()));
                    }
                } catch (Exception e) {
                    // logging and update next channel
                    LOG.info("Error occured during the channel parsings: " + channel.getTitle(), e);
                }
            }
        }
        LOG.info("End of updating feeds");
    }

    /** Delete old feeds for all users every day at 00:00 */
    @Scheduled(cron = "0 0 0 * * ?")
    public void deleteFeeds() {
        LOG.info("Start of deletion old feeds");
        userRepository
                .findAll()
                .forEach(
                        user -> {
                            LOG.info("Delete feeds for user: " + user.getPrincipalName());
                            int deleted =
                                    feedService.deleteOldFeeds(
                                            user.getPrincipalName(),
                                            user.getSettings().getDeleteAfter());
                            LOG.info(deleted + " was deleted for");
                        });
        LOG.info("End of deletion old feeds");
    }
}
