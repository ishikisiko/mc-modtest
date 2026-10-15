package com.example.myvillage.cultivation.fate;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server-thread-only, ephemeral sessions. Never serialized into either player attachment. */
public final class SteleSessionBook {
    private final Map<UUID, Session> sessions = new HashMap<>();
    private long sequence;

    public Session open(UUID player, String dimension, long position) {
        Session session = new Session(Math.incrementExact(sequence), dimension, position);
        sequence = session.token();
        sessions.put(player, session);
        return session;
    }

    public Session current(UUID player) { return sessions.get(player); }

    public boolean validate(UUID player, long token, String dimension, boolean playerValid,
            boolean testingStele, boolean inRange) {
        Session session = sessions.get(player);
        // A delayed request for an older screen must never invalidate a newer session.
        if (session == null || token <= 0 || session.token() != token) return false;
        if (!playerValid || !session.dimension().equals(dimension) || !testingStele || !inRange) {
            sessions.remove(player);
            return false;
        }
        return true;
    }

    public boolean close(UUID player, long token) {
        Session session = sessions.get(player);
        if (session == null || session.token() != token) return false;
        sessions.remove(player);
        return true;
    }

    public void forget(UUID player) { sessions.remove(player); }
    public Map<UUID, Session> snapshot() { return Map.copyOf(sessions); }
    public void clear() { sessions.clear(); }

    public record Session(long token, String dimension, long position) { }
}
