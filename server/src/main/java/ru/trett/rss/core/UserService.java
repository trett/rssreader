package ru.trett.rss.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import ru.trett.rss.models.Settings;
import ru.trett.rss.models.User;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

@Service
public class UserService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    public UserService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final String GET_USERS =
            "SELECT principal_name, settings, email FROM public.user u";

    public List<User> getUsers() {
        return jdbcTemplate.query(GET_USERS, new UserRowMapper());
    }

    private static final String GET_USER = GET_USERS + " WHERE u.principal_name = ?";

    public Optional<User> getUser(String userName) {
        var users = jdbcTemplate.query(GET_USER, new UserRowMapper(), userName);
        return users.isEmpty() ? Optional.empty() : Optional.of(users.get(0));
    }

    private static final String INSERT_USER =
            "INSERT INTO public.user(principal_name, email, settings) VALUES(?,?,?)";

    public void save(User user) {
        try {
            jdbcTemplate.update(
                    INSERT_USER,
                    user.getPrincipalName(),
                    user.getEmail(),
                    JSON.writeValueAsString(user.getSettings()));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Invalid json value", e);
        }
    }

    private static final String UPDATE_USER =
            "UPDATE public.user SET email=?, settings=? WHERE principal_name=?";

    public void update(User user) {
        try {
            jdbcTemplate.update(
                    UPDATE_USER,
                    user.getEmail(),
                    JSON.writeValueAsString(user.getSettings()),
                    user.getPrincipalName());
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Invalid json value", e);
        }
    }

    private class UserRowMapper implements RowMapper<User> {

        @Override
        public User mapRow(ResultSet rs, int row) throws SQLException {
            try {
                User user = new User();
                user.setPrincipalName(rs.getString("principal_name"));
                user.setEmail(rs.getString("email"));
                user.setSettings(JSON.readValue(rs.getString("settings"), Settings.class));
                return user;
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Error occured during parse settings");
            }
        }
    }
}
