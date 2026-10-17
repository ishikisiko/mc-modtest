package com.example.myvillage.cultivation.fate;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Saved owner/target cooldowns. Active deadlines are never evicted to make room for a proc. */
public record FireSpiritCooldownState(Map<String, Long> deadlines) {
    public static final int MAX_TARGETS = 64;
    public static final FireSpiritCooldownState EMPTY = new FireSpiritCooldownState(Map.of());
    private static final Codec<Map<String, Long>> DEADLINES = Codec.unboundedMap(Codec.STRING, Codec.LONG)
            .validate(values -> valid(values) ? DataResult.success(values)
                    : DataResult.error(() -> "Invalid bounded fire spirit cooldowns"));
    public static final Codec<FireSpiritCooldownState> CODEC = DEADLINES
            .optionalFieldOf("deadlines", Map.of()).codec()
            .xmap(FireSpiritCooldownState::new, FireSpiritCooldownState::deadlines);

    public FireSpiritCooldownState {
        if (!valid(deadlines)) throw new IllegalArgumentException("Invalid bounded fire spirit cooldowns");
        deadlines = Map.copyOf(deadlines);
    }

    private static boolean valid(Map<String, Long> values) {
        if (values == null || values.size() > MAX_TARGETS) return false;
        for (var entry : values.entrySet()) {
            try {
                if (!UUID.fromString(entry.getKey()).toString().equals(entry.getKey())
                        || entry.getValue() == null || entry.getValue() < 0) return false;
            } catch (IllegalArgumentException | NullPointerException ignored) { return false; }
        }
        return true;
    }

    public long readyAt(UUID target) { return deadlines.getOrDefault(target.toString(), 0L); }

    public boolean canRecord(UUID target, long now) {
        return readyAt(target) <= now && (deadlines.containsKey(target.toString())
                || deadlines.values().stream().filter(value -> value > now).count() < MAX_TARGETS);
    }

    public FireSpiritCooldownState record(UUID target, long now, int cooldownTicks) {
        if (now < 0 || cooldownTicks <= 0 || !canRecord(target, now)) return this;
        var next = new LinkedHashMap<String, Long>();
        deadlines.forEach((id, deadline) -> { if (deadline > now) next.put(id, deadline); });
        next.put(target.toString(), now > Long.MAX_VALUE - cooldownTicks ? Long.MAX_VALUE : now + cooldownTicks);
        return new FireSpiritCooldownState(next);
    }
}
