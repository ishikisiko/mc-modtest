package com.example.myvillage.sim;

import java.util.List;

/**
 * One chronicle entry. Fixed fields (design §2.1): {@code actors} are person ids with the subject
 * first; a param starting with {@code @} is a language key to translate, anything else is literal.
 *
 * @param importance 1 minor, 2 notable, 3 major
 * @param causeId    id of the event that caused this one, or -1
 */
public record SimEvent(
        long id,
        long day,
        String type,
        int importance,
        List<Integer> actors,
        List<Integer> sects,
        String regionId,
        long causeId,
        String textKey,
        List<String> params) {

    public SimEvent {
        actors = List.copyOf(actors);
        sects = List.copyOf(sects);
        params = List.copyOf(params);
        regionId = regionId == null ? "" : regionId;
    }

    /** The subject (first actor), or -1. */
    public int subject() {
        return actors.isEmpty() ? -1 : actors.get(0);
    }
}
