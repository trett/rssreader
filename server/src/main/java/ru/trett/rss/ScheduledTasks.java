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
    private final UserService userService;
    private final RssParser rssParser;
    private final RestTemplate restTemplate;

    @Autowired
    public ScheduledTasks(
            ChannelService channelService,
            FeedService feedService,
            UserService userService,
            RssParser rssParser,
            RestTemplate restTemplate) {
        this.channelService = channelService;
        this.feedService = feedService;
        this.userService = userService;
        this.rssParser = rssParser;
        this.restTemplate = restTemplate;
    }

    @Scheduled(cron = "0 0/15 * * * ?")
    public void updateFeeds() {
        LOG.info("Starting of update feeds at " + LocalDateTime.now());
        for (var user : userService.getUsers()) {
            LOG.info("Starting of update feeds for user: " + user.principalName);
            for (var channel : channelService.findByUser(user.principalName)) {
                var inserted =
                        restTemplate.execute(
                                URI.create(channel.channelLink),
                                HttpMethod.GET,
                                null,
                                response -> {
                                    var since =
                                            LocalDateTime.now()
                                                    .minusDays(user.settings.deleteAfter);
                                    try {
                                        var feeds =
                                                rssParser
                                                        .parse(response.getBody())
                                                        .feedItems
                                                        .stream()
                                                        .filter(feed -> feed.pubDate.isAfter(since))
                                                        .collect(Collectors.toList());
                                        return feedService.saveAll(feeds, channel.id);
                                    } catch (Exception e) {
                                        // logging and update next channel
                                        LOG.info(
                                                "Error occured during the channel parsings: "
                                                        + channel.title,
                                                e);
                                        return 0;
                                    }
                                });
                LOG.info(
                        MessageFormat.format(
                                "{0} items was updated for ''{1}''", inserted, channel.title));
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
