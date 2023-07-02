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

    private final UserService userService;

    @Autowired
    public SettingsController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public Settings getSettings(Principal principal) {
        var userName = principal.getName();
        LOG.info("Getting settings for the user: " + userName);
        return userService.getUser(userName).map(u -> u.settings).orElseGet(Settings::new);
    }

    @PostMapping
    public void updateSettings(@RequestBody Settings settings, Principal principal) {
        var userName = principal.getName();
        LOG.info("Updating settings for the user:" + userName);
        userService
                .getUser(userName)
                .ifPresent(
                        user -> {
                            user.settings = settings;
                            userService.update(user);
                        });
    }
}
