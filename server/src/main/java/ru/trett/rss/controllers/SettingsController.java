package ru.trett.rss.controllers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import ru.trett.rss.core.UserService;
import ru.trett.rss.models.Settings;

import java.security.Principal;

@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private static final Logger LOG = LoggerFactory.getLogger(SettingsController.class);

    private UserService userService;

    @Autowired
    public SettingsController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public Settings getSettings(Principal principal) {
        String userName = principal.getName();
        LOG.info("Update settings for user: " + userName);
        return userService
                .getUser(userName)
                .map(u -> u.getSettings())
                .orElseGet(() -> new Settings());
    }

    @PostMapping
    public void updateSettings(@RequestBody Settings settings, Principal principal) {
        String userName = principal.getName();
        LOG.info("Update settings for user:" + userName);
        userService
                .getUser(userName)
                .ifPresent(
                        user -> {
                            user.setSettings(settings);
                            userService.update(user);
                        });
    }
}
