package ru.trett.rss.controllers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import ru.trett.rss.dao.UserRepository;
import ru.trett.rss.models.Settings;
import ru.trett.rss.models.User;

import java.security.Principal;

@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private Logger logger = LoggerFactory.getLogger(SettingsController.class);
    
    private UserRepository userRepository;

    @Autowired
    public SettingsController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping
    public Settings getSettings(Principal principal) {
        String userName = principal.getName();
        logger.info("Update settings for user: " + userName);
        return userRepository.findByPrincipalName(userName).getSettings();
    }

    @PostMapping
    public void updateSettings(@RequestBody Settings settings, Principal principal) {
        String userName = principal.getName();
        logger.info("Update settings for user:" + userName);
        User user = userRepository.findByPrincipalName(userName);
        user.setSettings(settings);
        userRepository.save(user);
    }
}
