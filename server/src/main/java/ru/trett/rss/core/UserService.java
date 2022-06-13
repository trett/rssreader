package ru.trett.rss.core;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import ru.trett.rss.converter.SettingsConverter;
import ru.trett.rss.models.User;

@Service
public class UserService {

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public UserService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final String GET_USER =
            "SELECT settings, email FROM \"user\" u WHERE u.principal_name = ?";

    public User getUser(String userName) {
        return jdbcTemplate.queryForObject(
                GET_USER,
                (rs, rowNum) -> {
                    User user = new User();
                    user.setPrincipalName(userName);
                    user.setEmail(rs.getString("email"));
                    user.setSettings(
                            new SettingsConverter()
                                    .convertToEntityAttribute(rs.getString("settings")));
                    return user;
                },
                userName);
    }
}
