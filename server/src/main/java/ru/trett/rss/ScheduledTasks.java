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

    private static final Logger LOGGER = LoggerFactory.getLogger(ScheduledTasks.class);
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
        LOGGER.info("Starting of update feeds at " + LocalDateTime.now());
        for (User user : userRepository.findAll()) {
            LOGGER.info("Starting of update feeds for user: " + user.getPrincipalName());
            for (Channel channel : channelRepository.findByUserEager(user)) {
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
                        LOGGER.info(
                                MessageFormat.format(
                                        "{0} items was updated for ''{1}''",
                                        inserted, channel.getTitle()));
                    }
                } catch (Exception e) {
                    // logging and update next channel
                    LOGGER.info(
                            "Error occured during the channel parsings: " + channel.getTitle(), e);
                }
            }
        }
        LOGGER.info("End of updating feeds");
    }

    /** Delete old feeds for all users every day at 00:00 */
    @Scheduled(cron = "0 0 0 * * ?")
    public void deleteFeeds() {
        LOGGER.info("Starting delete old feeds");
        userRepository
                .findAll()
                .forEach(
                        user -> {
                            LOGGER.info("Delete feeds for user: " + user.getPrincipalName());
                            channelRepository
                                    .findByUserEager(user)
                                    .forEach(
                                            channel ->
                                                    channel.getFeedItems().stream()
                                                            .filter(
                                                                    feedItem ->
                                                                            feedItem.getPubDate()
                                                                                    .isBefore(
                                                                                            LocalDateTime
                                                                                                    .now()
                                                                                                    .minusDays(
                                                                                                            user.getSettings()
                                                                                                                    .getDeleteAfter())))
                                                            .peek(
                                                                    feedItem ->
                                                                            LOGGER.info(
                                                                                    "Deleting feed:"
                                                                                            + " "
                                                                                            + feedItem
                                                                                                    .getGuid()))
                                                            .forEach(
                                                                    feedItem ->
                                                                            feedService.delete(
                                                                                    feedItem
                                                                                            .getId())));
                        });
        LOGGER.info("End of delete old feeds");
    }
}
