package com.trett.rss.models;

public class Record {
    private String text;

    private String link;

    public Record(String text, String link) {
        this.text = text;
        this.link = link;
    }

    /**
     * @return the text
     */
    public String getText() {
        return text;
    }

    /**
     * @return the link
     */
    public String getLink() {
        return link;
    }

}