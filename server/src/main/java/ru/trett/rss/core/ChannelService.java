package ru.trett.rss.core;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import ru.trett.rss.models.Channel;

import java.util.List;

import javax.validation.constraints.NotEmpty;

@Service
public class ChannelService {

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public ChannelService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final String INSERT_CHANNEL =
            "INSERT INTO public.channels(channel_link, title, link, user_principal_name) VALUES(?,"
                    + " ?, ?, ?)";

    public int save(Channel channel, @NotEmpty String principalName) {
        return jdbcTemplate.update(
                INSERT_CHANNEL, channel.channelLink, channel.title, channel.link, principalName);
    }

    private static final String DELETE_CHANNEL = "DELETE FROM public.channels WHERE id=?";

    public int delete(long channelId) {
        return jdbcTemplate.update(DELETE_CHANNEL, channelId);
    }

    private static final String FIND_BY_USER =
            "SELECT id, channel_link, title, link from public.channels WHERE user_principal_name=?";

    public List<Channel> findByUser(String userName) {
        return jdbcTemplate.query(
                FIND_BY_USER,
                (rs, idx) -> {
                    var channel = new Channel();
                    channel.id = rs.getLong("id");
                    channel.channelLink = rs.getString("channel_link");
                    channel.title = rs.getString("title");
                    channel.link = rs.getString("link");
                    return channel;
                },
                userName);
    }
}
