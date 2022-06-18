package ru.trett.rss.controllers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import ru.trett.rss.core.ClientException;
import ru.trett.rss.core.FeedService;
import ru.trett.rss.dao.ChannelRepository;
import ru.trett.rss.dao.UserRepository;
import ru.trett.rss.models.Channel;
import ru.trett.rss.models.User;
import ru.trett.rss.parser.RssParser;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.security.Principal;
import java.text.MessageFormat;
import java.time.LocalDateTime;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

@RestController
@RequestMapping(path = "/api/channel")
public class ChannelsController {

    private static final Logger LOG = LoggerFactory.getLogger(ChannelsController.class);

    private final ChannelRepository channelRepository;
    private final FeedService feedService;
    private final UserRepository userRepository;
    private final RestTemplate restTemplate;

    @Autowired
    public ChannelsController(
            ChannelRepository channelRepository,
            FeedService feedService,
            UserRepository userRepository,
            RestTemplate restTemplate) {
        this.channelRepository = channelRepository;
        this.feedService = feedService;
        this.userRepository = userRepository;
        this.restTemplate = restTemplate;
    }

    @GetMapping(path = "/all")
    public Iterable<Channel> getChannels(Principal principal) {
        String userName = principal.getName();
        LOG.info("Retrieving all channels for principal: " + userName);
        return channelRepository.findByUser(userRepository.findByPrincipalName(userName));
    }

    @GetMapping(path = "/refresh")
    public void refresh(Principal principal) {
        String userName = principal.getName();
        String logMessage = "Updating channels for principal: " + userName + ". ";
        LOG.info(logMessage + "Start");
        ClientHttpRequestFactory requestFactory = restTemplate.getRequestFactory();
        try {
            for (Channel channel :
                    channelRepository.findByUser(userRepository.findByPrincipalName(userName))) {
                LOG.info("Starting update feeds for channel: " + channel.getTitle());
                ClientHttpRequest request =
                        requestFactory.createRequest(
                                URI.create(channel.getChannelLink()), HttpMethod.GET);
                ClientHttpResponse execute = request.execute();
                int deleteAfter =
                        userRepository.findByPrincipalName(userName).getSettings().getDeleteAfter();
                try (InputStream inputStream = execute.getBody()) {
                    var since = LocalDateTime.now().minusDays(deleteAfter);
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
            }
        } catch (Exception e) {
            throw new ClientException("Can't update feeds. Please try later", e);
        }
        LOG.info(logMessage + "End");
    }

    @PostMapping(path = "/add")
    public Long addFeed(@RequestBody @NotEmpty String link, Principal principal)
            throws IOException {
        link = link.trim();
        LOG.info("Adding channel with link: " + link);
        try {
            ClientHttpRequest request =
                    restTemplate
                            .getRequestFactory()
                            .createRequest(URI.create(link), HttpMethod.GET);
            try (InputStream inputStream = request.execute().getBody()) {
                Channel channel = new RssParser(inputStream).parse();
                User user = userRepository.findByPrincipalName(principal.getName());
                if (StreamSupport.stream(channelRepository.findByUser(user).spliterator(), false)
                        .anyMatch(channel::equals)) {
                    throw new RuntimeException("Channel already exist");
                }
                channel.setUser(user);
                channel.setChannelLink(link);
                return channelRepository.save(channel).getId();
            }
        } catch (IOException e) {
            throw new ClientException("URL is not valid");
        }
    }

    @PostMapping(path = "/delete")
    public String delete(@NotNull @RequestBody Long id, Principal principal) {
        LOG.info("Deleting channel with id: " + id);
        User user = userRepository.findByPrincipalName(principal.getName());
        for (Channel channel : channelRepository.findByUser(user)) {
            if (channel.getId() == id) {
                channelRepository.deleteById(id);
                return "deleted: " + id;
            }
        }
        throw new ClientException("Channel not found");
    }
}
