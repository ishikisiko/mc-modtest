package com.example.myvillage.sim.social;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * Persistent social state owned by WorldSim. Player membership remains in PlayerMember; this
 * registry also contains players who have never joined a sect. No mutable collections escape it.
 */
public final class SocialState {
    final TreeMap<String, PlayerActor> players = new TreeMap<>();
    final TreeMap<Pair, Acquaintance> acquaintances = new TreeMap<>();

    static final class PlayerActor {
        final String playerId;
        String name;
        final long firstSeenDay;
        long lastSeenDay;

        PlayerActor(String playerId, String name, long firstSeenDay, long lastSeenDay) {
            this.playerId = playerId;
            this.name = name;
            this.firstSeenDay = firstSeenDay;
            this.lastSeenDay = lastSeenDay;
        }
    }

    record Pair(int personId, String playerId) implements Comparable<Pair> {
        @Override
        public int compareTo(Pair other) {
            int order = Integer.compare(personId, other.personId);
            return order != 0 ? order : playerId.compareTo(other.playerId);
        }
    }

    static final class Acquaintance {
        int familiarity;
        long firstConversationDay = -1;
        long lastConversationDay = -1;
        /** Oldest first. Durable first/last days survive eviction from this bounded history. */
        final List<Long> conversationDays = new ArrayList<>();
    }
}
