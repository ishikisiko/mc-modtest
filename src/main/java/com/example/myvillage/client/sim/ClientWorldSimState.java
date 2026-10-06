package com.example.myvillage.client.sim;

import com.example.myvillage.sim.runtime.net.WorldSimQuery;
import com.example.myvillage.sim.runtime.net.WorldSimSnapshot;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * The client's read-only cache of world-ledger answers for the 天下 page: one
 * {@link WorldSimSnapshot} per {@link WorldSimQuery}. The page asks through {@link #request} and
 * reads through {@link #latest}; it never holds a ledger of its own. The network layer installs
 * the sender and feeds answers through {@link #receive}. No Minecraft types, so the class loads on
 * either side.
 *
 * <p>{@link #request} is throttled: the same query goes out at most once per {@link #MIN_REPEAT_NANOS},
 * so a page may call it every frame while a view is open. The server throttles too.
 */
public final class ClientWorldSimState {
    /** The same query is sent at most this often (nanoseconds): 2.5 s. */
    public static final long MIN_REPEAT_NANOS = 2_500_000_000L;

    private static final Map<WorldSimQuery, WorldSimSnapshot> SNAPSHOTS = new ConcurrentHashMap<>();
    private static final Map<WorldSimQuery, Long> SENT_AT = new ConcurrentHashMap<>();
    private static volatile Consumer<WorldSimQuery> sender = ignored -> { };

    private ClientWorldSimState() {
    }

    /** Installed once by the payload registration; sends a query to the server. */
    public static void installSender(Consumer<WorldSimQuery> newSender) {
        sender = Objects.requireNonNull(newSender, "newSender");
    }

    /** The latest answer to this query, if any has arrived since the last {@link #clear}. */
    public static Optional<WorldSimSnapshot> latest(WorldSimQuery query) {
        return Optional.ofNullable(SNAPSHOTS.get(query));
    }

    /**
     * Sends the query unless the same one went out within {@link #MIN_REPEAT_NANOS}.
     *
     * @return whether it was sent now
     */
    public static boolean request(WorldSimQuery query) {
        Objects.requireNonNull(query, "query");
        long now = System.nanoTime();
        Long last = SENT_AT.get(query);
        if (last != null && now - last < MIN_REPEAT_NANOS) {
            return false;
        }
        SENT_AT.put(query, now);
        sender.accept(query);
        return true;
    }

    /** True while a query has been sent and no answer has arrived for it yet. */
    public static boolean awaiting(WorldSimQuery query) {
        return SENT_AT.containsKey(query) && !SNAPSHOTS.containsKey(query);
    }

    /** Stores an answer under its own query; called by the network layer on the client thread. */
    public static void receive(WorldSimSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        SNAPSHOTS.put(snapshot.query(), snapshot);
    }

    /** Forgets every answer and send time (on disconnect). */
    public static void clear() {
        SNAPSHOTS.clear();
        SENT_AT.clear();
    }
}
