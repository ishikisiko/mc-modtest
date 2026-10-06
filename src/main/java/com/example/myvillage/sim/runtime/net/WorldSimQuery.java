package com.example.myvillage.sim.runtime.net;

import java.util.Objects;

/**
 * One bounded, read-only question the client asks the world ledger (命簿) for the 天下 page of the
 * H panel. It carries nothing the server would act on: a kind, an id and a short text. The answer
 * is a {@link WorldSimSnapshot} keyed by this record, so equal queries share one cache slot.
 *
 * @param kind what is asked
 * @param id   a sect id ({@link Kind#SECT}) or person id ({@link Kind#PERSON}); -1 otherwise
 * @param text a name fragment ({@link Kind#PERSON_SEARCH}), trimmed and cut to {@link #MAX_TEXT}; "" otherwise
 */
public record WorldSimQuery(Kind kind, int id, String text) {
    /** Longest search text accepted (characters). */
    public static final int MAX_TEXT = 32;

    public enum Kind {
        /** Era date, settlement state, population, realms, sects, the five foremost people. */
        OVERVIEW,
        /** Every sect, active and destroyed, capped. */
        SECTS,
        /** One sect in detail with its members at the sect and its recent notable events. */
        SECT,
        /** People whose name or Daoist title contains the text, living first. */
        PERSON_SEARCH,
        /** One person, living or dead, with relations and recent events. */
        PERSON,
        /** The latest notable and major events, oldest first, with their causes. */
        CHRONICLE,
        /** The asking player's region: seated sects, people present, recent events. */
        HERE
    }

    public WorldSimQuery {
        Objects.requireNonNull(kind, "kind");
        String cleaned = text == null ? "" : text.strip();
        if (cleaned.length() > MAX_TEXT) {
            cleaned = cleaned.substring(0, MAX_TEXT);
        }
        text = kind == Kind.PERSON_SEARCH ? cleaned : "";
        id = kind == Kind.SECT || kind == Kind.PERSON ? Math.max(-1, id) : -1;
    }

    public static WorldSimQuery overview() {
        return new WorldSimQuery(Kind.OVERVIEW, -1, "");
    }

    public static WorldSimQuery sects() {
        return new WorldSimQuery(Kind.SECTS, -1, "");
    }

    public static WorldSimQuery sect(int sectId) {
        return new WorldSimQuery(Kind.SECT, sectId, "");
    }

    public static WorldSimQuery personSearch(String text) {
        return new WorldSimQuery(Kind.PERSON_SEARCH, -1, text);
    }

    public static WorldSimQuery person(int personId) {
        return new WorldSimQuery(Kind.PERSON, personId, "");
    }

    public static WorldSimQuery chronicle() {
        return new WorldSimQuery(Kind.CHRONICLE, -1, "");
    }

    public static WorldSimQuery here() {
        return new WorldSimQuery(Kind.HERE, -1, "");
    }
}
