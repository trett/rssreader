package com.trett.rss.models;

import com.trett.rss.converter.SettingsConverter;

import javax.persistence.*;
import javax.validation.constraints.NotEmpty;
import java.util.Set;

@Entity
public class User {

    @Id
    @NotEmpty
    @Column(name = "principal_name", unique = true, updatable = false)
    private String principalName;

    @NotEmpty
    private String email;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<Channel> channels;

    @Convert(converter = SettingsConverter.class)
    private Settings settings;

    public User() {
    }

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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        User user = (User) o;

        if (!principalName.equals(user.principalName)) return false;
        return email.equals(user.email);
    }

    @Override
    public int hashCode() {
        int result = principalName.hashCode();
        result = 31 * result + email.hashCode();
        return result;
    }
}
