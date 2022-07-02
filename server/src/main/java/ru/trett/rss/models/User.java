package ru.trett.rss.models;

import java.util.Set;

public class User {

    private String principalName;

    private String email;

    private Set<Channel> channels;

    private Settings settings;

    public User() {}

    public User(String principalName, String email) {
        this.principalName = principalName;
        this.email = email;
    }

    public String getPrincipalName() {
        return principalName;
    }

    public void setPrincipalName(String principalName) {
        this.principalName = principalName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Set<Channel> getChannels() {
        return channels;
    }

    public void setChannels(Set<Channel> channels) {
        this.channels = channels;
    }

    public Settings getSettings() {
        return settings;
    }

    public void setSettings(Settings settings) {
        this.settings = settings;
    }
}
