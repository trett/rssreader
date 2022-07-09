package ru.trett.rss;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import ru.trett.rss.core.ChannelService;
import ru.trett.rss.core.FeedService;
import ru.trett.rss.core.UserService;
import ru.trett.rss.parser.RssParser;

import java.net.URI;
import java.text.MessageFormat;
import java.time.LocalDateTime;
import java.util.stream.Collectors;

@Component
public class ScheduledTasks {

    private static final Logger LOG = LoggerFactory.getLogger(ScheduledTasks.class);

    private final ChannelService channelService;
    private final FeedService feedService;
    private final RestTemplate restTemplate;
    private final UserService userService;

    @Autowired
    public ScheduledTasks(
            ChannelService channelService,
            FeedService feedService,
            UserService userRepository,
            RestTemplate restTemplate) {
        this.channelService = channelService;
        this.feedService = feedService;
        this.userService = userRepository;
        this.restTemplate = restTemplate;
    }

    @Scheduled(cron = "0 0/30 * * * ?")
    public void updateFeeds() {
        var requestFactory = restTemplate.getRequestFactory();
        LOG.info("Starting of update feeds at " + LocalDateTime.now());
        for (var user : userService.getUsers()) {
            LOG.info("Starting of update feeds for user: " + user.principalName);
            for (var channel : channelService.findByUser(user.principalName)) {
                try {
                    var request =
                            requestFactory.createRequest(
                                    URI.create(channel.channelLink), HttpMethod.GET);
                    var execute = request.execute();
                    try (var is = execute.getBody()) {
                        var since = LocalDateTime.now().minusDays(user.settings.deleteAfter);
                        var feeds =
                                new RssParser(is)
                                        .parse().feedItems.stream()
                                                .filter(feed -> feed.pubDate.isAfter(since))
                                                .collect(Collectors.toList());
                        var inserted = feedService.saveAll(feeds, channel.id);
                        LOG.info(
                                MessageFormat.format(
                                        "{0} items was updated for ''{1}''",
                                        inserted, channel.title));
                    }
                } catch (Exception e) {
                    // logging and update next channel
                    LOG.info("Error occured during the channel parsings: " + channel.title, e);
                }
            }
        }
        LOG.info("End of updating feeds");
    }

    @Scheduled(cron = "0 0 0 * * ?")
    public void deleteFeeds() {
        LOG.info("Start of deletion old feeds");
        userService
                .getUsers()
                .forEach(
                        user -> {
                            LOG.info("Delete feeds for user: " + user.principalName);
                            int deleted =
                                    feedService.deleteOldFeeds(
                                            user.principalName, user.settings.deleteAfter);
                            LOG.info(deleted + " was deleted for");
                        });
        LOG.info("End of deletion old feeds");
    }
}
