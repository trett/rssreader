package ru.trett.rss.models;

import jakarta.validation.constraints.NotEmpty;

import java.util.ArrayList;
import java.util.List;

public class User {

    @NotEmpty public String principalName;
    @NotEmpty public String email;
    public List<Channel> channels;
    public Settings settings;

    public User(String principalName, String email) {
        this.principalName = principalName;
        this.email = email;
        this.channels = new ArrayList<>();
        this.settings = new Settings();
    }
}
