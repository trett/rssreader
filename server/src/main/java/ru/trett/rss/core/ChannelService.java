package ru.trett.rss.core;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import ru.trett.rss.models.Channel;

import java.util.List;

@Service
public class ChannelService {

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public ChannelService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final String INSERT_CHANNEL =
            "INSERT INTO public.channel(channel_link, title, link, user_principal_name)"
                    + " VALUES(?, ?, ?, ?)";

    public int save(Channel channel) {
        return jdbcTemplate.update(
                INSERT_CHANNEL,
                channel.channelLink,
                channel.title,
                channel.link,
                channel.user.principalName);
    }

    private static final String DELETE_CHANNEL = "DELETE FROM public.channel WHERE id=?";

    public int delete(long channelId) {
        return jdbcTemplate.update(DELETE_CHANNEL, channelId);
    }

    private static final String FIND_BY_USER =
            "SELECT id, channel_link, title, link, user_principal_name from public.channel WHERE"
                    + " user_principal_name=?";

    public List<Channel> findByUser(String userName) {
        return jdbcTemplate.query(FIND_BY_USER, new BeanPropertyRowMapper<>(Channel.class), userName);
    }
}
