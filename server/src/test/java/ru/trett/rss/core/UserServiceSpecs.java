package ru.trett.rss.core;

import static org.junit.Assert.assertEquals;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.test.jdbc.JdbcTestUtils;

import ru.trett.rss.models.Settings;
import ru.trett.rss.models.User;

import java.util.Optional;

import javax.sql.DataSource;

public class UserServiceSpecs {

    private JdbcTemplate jdbcTemplate;
    private UserService userService;

    @Before
    public void init() {
        DataSource dataSource =
                new EmbeddedDatabaseBuilder()
                        .setType(EmbeddedDatabaseType.H2)
                        .addScript("classpath:schema.sql")
                        .addScript("classpath:users_test_data.sql")
                        .build();

        jdbcTemplate = new JdbcTemplate(dataSource);
        userService = new UserService(jdbcTemplate);
    }

    @After
    public void tearDown() {
        JdbcTestUtils.dropTables(jdbcTemplate, "feed_item", "channel", "user");
    }

    @Test
    public void testSaveUser() {
        User user = new User("345", "email@example.com");
        var settings = new Settings();
        settings.deleteAfter = 10;
        user.settings = settings;
        userService.save(user);
        assertEquals(
                Optional.of(settings.deleteAfter),
                userService.getUser(user.principalName).map(u -> u.settings.deleteAfter));
    }

    @Test
    public void testUpdateUser() {
        var user = userService.getUser("123").get();
        user.email = "new@email.com";
        userService.update(user);
        assertEquals(Optional.of("new@email.com"), userService.getUser("123").map(u -> u.email));
    }

    @Test
    public void testGetUser() {
        assertEquals(
                Optional.of("example2@test.com"), userService.getUser("234").map(u -> u.email));
    }

    @Test
    public void testGetUsers() {
        assertEquals(2, userService.getUsers().size());
    }
}
