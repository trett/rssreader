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

    private static final ObjectMapper JSON = new ObjectMapper();

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public UserService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final String GET_USERS =
            "SELECT principal_name, settings, email FROM public.users u";

    public List<User> getUsers() {
        return jdbcTemplate.query(GET_USERS, new UserRowMapper());
    }

    private static final String GET_USER = GET_USERS + " WHERE u.principal_name = ?";

    public Optional<User> getUser(String userName) {
        var users = jdbcTemplate.query(GET_USER, new UserRowMapper(), userName);
        return users.isEmpty() ? Optional.empty() : Optional.of(users.get(0));
    }

    private static final String INSERT_USER =
            "INSERT INTO public.users(principal_name, email, settings) VALUES(?,?,?)";

    public void save(User user) {
        try {
            jdbcTemplate.update(
                    INSERT_USER,
                    user.principalName,
                    user.email,
                    JSON.writeValueAsString(user.settings));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Invalid json value", e);
        }
    }

    private static final String UPDATE_USER =
            "UPDATE public.users SET email=?, settings=? WHERE principal_name=?";

    public void update(User user) {
        try {
            jdbcTemplate.update(
                    UPDATE_USER,
                    user.email,
                    JSON.writeValueAsString(user.settings),
                    user.principalName);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Invalid json value", e);
        }
    }

    private static class UserRowMapper implements RowMapper<User> {

        @Override
        public User mapRow(ResultSet rs, int row) throws SQLException {
            try {
                var principalName = rs.getString("principal_name");
                var email = rs.getString("email");
                var user = new User(principalName, email);
                user.settings = JSON.readValue(rs.getString("settings"), Settings.class);
                return user;
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Error occured during parse settings");
            }
        }
    }
}
