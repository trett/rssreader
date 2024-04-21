package ru.trett.rss.models;

import java.util.ArrayList;
import java.util.List;

import javax.validation.constraints.NotEmpty;

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
