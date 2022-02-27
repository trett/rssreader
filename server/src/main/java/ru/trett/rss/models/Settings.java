package ru.trett.rss.models;

import javax.validation.constraints.Size;

public class Settings {

    private boolean hideRead;

    @Size(max = 2)
    private int deleteAfter;

    public boolean isHideRead() {
        return hideRead;
    }

    public void setHideRead(boolean hideRead) {
        this.hideRead = hideRead;
    }

    public int getDeleteAfter() {
        return deleteAfter;
    }

    public void setDeleteAfter(int deleteAfter) {
        this.deleteAfter = deleteAfter;
    }
}
