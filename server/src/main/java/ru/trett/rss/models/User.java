package ru.trett.rss.models;

import ru.trett.rss.converter.SettingsConverter;

import java.util.Set;

import javax.persistence.*;
import javax.validation.constraints.NotEmpty;

@Entity
public class User {

    @Id
    @NotEmpty
    @Column(name = "principal_name", unique = true, updatable = false)
    private String principalName;

    @NotEmpty private String email;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Channel> channels;

    @Convert(converter = SettingsConverter.class)
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
