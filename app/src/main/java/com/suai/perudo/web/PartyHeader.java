package com.suai.perudo.web;

/**
 * Created by dmitry on 09.12.18.
 */

public class PartyHeader {
    private long id;
    private String title;
    private int hash;

    public PartyHeader(Party party) {
        this.id = party.getId();
        this.title = party.getTitle();
        this.hash = party.hashCode();
    }

    public long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public int getHash() {
        return hash;
    }
}
