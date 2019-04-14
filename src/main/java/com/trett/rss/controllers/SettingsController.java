package com.trett.rss.controllers;

import com.trett.rss.dao.UserRepository;
import com.trett.rss.models.Settings;
import com.trett.rss.models.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/settings")
public class SettingsController {

    private UserRepository userRepository;

    @Autowired
    public SettingsController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping
    public Settings getSettings(Principal principal) {
        return userRepository.findByPrincipalName(principal.getName()).getSettings();
    }

    @PostMapping
    public void updateSettings(@RequestBody Settings settings, Principal principal) {
        User user = userRepository.findByPrincipalName(principal.getName());
        user.setSettings(settings);
        userRepository.save(user);
    }
}
