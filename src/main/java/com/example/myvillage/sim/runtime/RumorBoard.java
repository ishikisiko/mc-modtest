package com.example.myvillage.sim.runtime;

import com.example.myvillage.sim.SimEvent;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Who hears which rumor, and when (no Minecraft types, so it is unit-tested). Each player has a
 * capped queue (the oldest waiting rumor is dropped when it overflows) and a sliding one-minute
 * window of rumors already told; {@link #due} releases waiting rumors in the order they were offered,
 * within every player's per-minute budget.
 *
 * @param <K> player key
 * @param <T> message
 */
public final class RumorBoard<K, T> {
    public static final long WINDOW_MILLIS = 60_000L;
    /** Importance of events every online player hears. */
    public static final int MAJOR = 3;
    /** Importance of events heard only by players in the event's region. */
    public static final int NOTABLE = 2;

    private final Map<K, Deque<Entry<K, T>>> waiting = new LinkedHashMap<>();
    private final Map<K, Deque<Long>> told = new HashMap<>();
    private long sequence;
    private long dropped;

    /** A rumor released for one player. */
    public record Entry<K, T>(K player, T message, long sequence) {
    }

    /**
     * The players who hear {@code event}: everyone for a major event, with those in the event's
     * region first; only the players in the event's region for a notable one; nobody otherwise.
     */
    public static <P> List<P> audience(SimEvent event, List<P> players, Function<P, Optional<String>> regionOf) {
        List<P> here = new ArrayList<>();
        List<P> elsewhere = new ArrayList<>();
        String region = event.regionId();
        for (P p : players) {
            boolean inRegion = !region.isEmpty() && regionOf.apply(p).map(region::equals).orElse(false);
            if (inRegion) {
                here.add(p);
            } else if (event.importance() >= MAJOR) {
                elsewhere.add(p);
            }
        }
        if (event.importance() < NOTABLE) {
            return List.of();
        }
        here.addAll(elsewhere);
        return here;
    }

    /** Queues a rumor for one player; when the queue holds more than {@code cap}, the oldest goes. */
    public void offer(K player, T message, int cap) {
        Deque<Entry<K, T>> q = waiting.computeIfAbsent(player, k -> new ArrayDeque<>());
        q.addLast(new Entry<>(player, message, sequence++));
        while (q.size() > Math.max(1, cap)) {
            q.removeFirst();
            dropped++;
        }
    }

    /** Releases the rumors that may be told now, oldest offer first, at most {@code perMinute} per player. */
    public List<Entry<K, T>> due(long nowMillis, int perMinute) {
        List<Entry<K, T>> out = new ArrayList<>();
        while (true) {
            Entry<K, T> next = null;
            for (Map.Entry<K, Deque<Entry<K, T>>> e : waiting.entrySet()) {
                Entry<K, T> head = e.getValue().peekFirst();
                if (head == null || told(e.getKey(), nowMillis) >= perMinute) {
                    continue;
                }
                if (next == null || head.sequence() < next.sequence()) {
                    next = head;
                }
            }
            if (next == null) {
                break;
            }
            waiting.get(next.player()).removeFirst();
            told.computeIfAbsent(next.player(), k -> new ArrayDeque<>()).addLast(nowMillis);
            out.add(next);
        }
        waiting.values().removeIf(Deque::isEmpty);
        return out;
    }

    private int told(K player, long nowMillis) {
        Deque<Long> times = told.get(player);
        if (times == null) {
            return 0;
        }
        while (!times.isEmpty() && times.peekFirst() <= nowMillis - WINDOW_MILLIS) {
            times.removeFirst();
        }
        return times.size();
    }

    public int waiting(K player) {
        Deque<Entry<K, T>> q = waiting.get(player);
        return q == null ? 0 : q.size();
    }

    /** Rumors dropped because a queue overflowed, since the board was made. */
    public long dropped() {
        return dropped;
    }

    public void forget(K player) {
        waiting.remove(player);
        told.remove(player);
    }

    public void clear() {
        waiting.clear();
        told.clear();
    }
}
