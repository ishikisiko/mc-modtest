package com.example.myvillage.combat.art;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server-thread ledger, keyed by art rather than hotbar slot or equipped stack.
 * Logout, respawn and dimension travel intentionally do not clear it. No offline entities retained.
 * Server stop clears transient cooldowns, consistently with other combat runtime state.
 */
public final class ArtCooldowns {
    private record Key(UUID player, String art) {}
    private final Map<Key, Long> deadlines = new HashMap<>();
    public int remaining(UUID player, String art, long now) {
        return ArtMath.remaining(now, deadlines.getOrDefault(new Key(player, art), now));
    }
    public boolean ready(UUID player, String art, long now) { return remaining(player, art, now) == 0; }
    public void start(UUID player, String art, long now, int duration) {
        deadlines.put(new Key(player, art), ArtMath.deadline(now, duration));
    }
    public void prune(long now) { deadlines.values().removeIf(deadline -> deadline <= now); }
    public void clear() { deadlines.clear(); }
}
