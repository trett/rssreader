package ru.trett.rss.models;

import java.util.Set;

import javax.validation.constraints.NotEmpty;

public class User {

    @NotEmpty public String principalName;
    @NotEmpty public String email;
    public Set<Channel> channels;
    public Settings settings;

    public User(String principalName, String email) {
        this.principalName = principalName;
        this.email = email;
    }
}
