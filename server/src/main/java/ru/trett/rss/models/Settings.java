package ru.trett.rss.models;

import jakarta.validation.constraints.Size;

public class Settings {

    public boolean hideRead;

    @Size(max = 2)
    public int deleteAfter = 7;
}
