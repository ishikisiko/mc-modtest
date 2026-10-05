package com.example.myvillage.sim.engine;

import com.example.myvillage.sim.SimEvent;
import com.example.myvillage.sim.SimObserver;
import com.example.myvillage.sim.SimRng;
import com.example.myvillage.sim.model.WorldState;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The append-only chronicle (design §3.5). Importance 2 and 3 are kept forever; importance 1 keeps
 * the latest N per living subject and is dropped when the subject dies. The per-subject index is
 * derived from the kept entries, so a restored world rebuilds exactly the same index.
 */
public final class Chronicle {
    private final WorldState state;
    private final Map<String, Integer> arities;
    private final Map<String, int[]> families;
    private final int minorPerPerson;
    private final Map<Integer, ArrayDeque<Long>> minorBySubject = new HashMap<>();
    private final Set<Long> dropped = new HashSet<>();
    private List<SimEvent> today = new ArrayList<>();
    private SimObserver observer;

    Chronicle(WorldState state, Map<String, Integer> arities, Map<String, int[]> families, int minorPerPerson) {
        this.state = state;
        this.arities = arities;
        this.families = families;
        this.minorPerPerson = minorPerPerson;
        for (SimEvent e : state.chronicle) {
            if (e.importance() == 1) {
                minorBySubject.computeIfAbsent(e.subject(), k -> new ArrayDeque<>()).addLast(e.id());
            }
        }
    }

    void setObserver(SimObserver observer) {
        this.observer = observer;
    }

    /** Starts a builder for an event on the current day. */
    public Builder event(String type, int importance) {
        return new Builder(type, importance);
    }

    /** Drops the subject's minor entries (at death). */
    void dropMinor(int subject) {
        ArrayDeque<Long> ids = minorBySubject.remove(subject);
        if (ids != null) {
            dropped.addAll(ids);
        }
    }

    /** Returns the events emitted since the last call and compacts dropped entries. */
    List<SimEvent> endDay() {
        if (!dropped.isEmpty()) {
            state.chronicle.removeIf(e -> dropped.contains(e.id()));
            dropped.clear();
        }
        List<SimEvent> out = today;
        today = new ArrayList<>();
        return out;
    }

    private long emit(Builder b) {
        Integer arity = arities.get(b.textKey);
        if (arity == null) {
            throw new IllegalStateException("unregistered text key " + b.textKey);
        }
        if (arity != b.params.size()) {
            throw new IllegalStateException(b.textKey + " takes " + arity + " params, got " + b.params);
        }
        if (b.importance == 1 && b.actors.isEmpty()) {
            throw new IllegalStateException("minor event " + b.type + " needs a subject");
        }
        SimEvent e = new SimEvent(state.nextEventId++, state.day, b.type, b.importance, b.actors, b.sects,
                b.regionId, b.causeId, b.textKey, b.params);
        state.chronicle.add(e);
        today.add(e);
        if (e.importance() == 1) {
            ArrayDeque<Long> ids = minorBySubject.computeIfAbsent(e.subject(), k -> new ArrayDeque<>());
            ids.addLast(e.id());
            while (ids.size() > minorPerPerson) {
                dropped.add(ids.pollFirst());
            }
        }
        if (observer != null) {
            observer.onEvent(e);
        }
        return e.id();
    }

    /** Fluent event builder; {@link #text} emits and returns the new event id. */
    public final class Builder {
        private final String type;
        private final int importance;
        private List<Integer> actors = List.of();
        private List<Integer> sects = List.of();
        private String regionId = "";
        private long causeId = -1;
        private String textKey;
        private List<String> params;

        private Builder(String type, int importance) {
            if (importance < 1 || importance > 3) {
                throw new IllegalArgumentException("importance " + importance);
            }
            this.type = type;
            this.importance = importance;
        }

        public Builder actors(Integer... ids) {
            this.actors = List.of(ids);
            return this;
        }

        public Builder sects(Integer... ids) {
            this.sects = List.of(ids);
            return this;
        }

        public Builder region(String regionId) {
            this.regionId = regionId == null ? "" : regionId;
            return this;
        }

        public Builder cause(long causeId) {
            this.causeId = causeId;
            return this;
        }

        /** Emits one variant of a line family, chosen by hash of (day, subject, family). */
        public long say(String family, String... params) {
            int[] f = families.get(family);
            if (f == null) {
                throw new IllegalStateException("unregistered line family " + family);
            }
            long subject = actors.isEmpty() ? (sects.isEmpty() ? 0 : -sects.get(0)) : actors.get(0);
            int variant = 1 + SimRng.at(state.seed, state.day, subject, Purpose.TEXT_VARIANT, family.hashCode())
                    .nextInt(f[1]);
            return text(family + "." + variant, params);
        }

        public long say(String family, Anchor params) {
            return say(family, params.build());
        }

        public long text(String textKey, String... params) {
            this.textKey = textKey;
            this.params = Arrays.asList(params);
            return emit(this);
        }
    }
}
