package com.example.myvillage.arrival;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** One authored source for polling, debounce, replay and animation timing. */
public record ArrivalRules(int pollTicks, int settleTicks, int exitGraceTicks, int repeatCooldownTicks,
        int regionDurationTicks, int townDurationTicks, int gapTicks, int fadeInTicks, int fadeOutTicks,
        Map<String, String> settlementNames) {
    public static final ArrivalRules INSTANCE = load();

    public ArrivalRules {
        settlementNames = Map.copyOf(settlementNames);
        if (pollTicks < 1 || settleTicks < pollTicks || exitGraceTicks < settleTicks
                || repeatCooldownTicks < exitGraceTicks || fadeInTicks < 1 || fadeOutTicks < 1 || gapTicks < 0
                || Math.min(regionDurationTicks, townDurationTicks) <= fadeInTicks + fadeOutTicks)
            throw new IllegalArgumentException("Invalid arrival timing");
    }

    public static ArrivalRules parse(JsonObject root) {
        var names = new HashMap<String, String>();
        for (var e : root.getAsJsonObject("settlement_names").entrySet()) {
            String name = e.getValue().getAsString();
            ArrivalLocation.text(name, false);
            names.put(e.getKey(), name);
        }
        return new ArrivalRules(integer(root, "poll_ticks"), integer(root, "settle_ticks"),
                integer(root, "exit_grace_ticks"), integer(root, "repeat_cooldown_ticks"),
                integer(root, "region_duration_ticks"), integer(root, "town_duration_ticks"),
                integer(root, "gap_ticks"), integer(root, "fade_in_ticks"), integer(root, "fade_out_ticks"), names);
    }

    private static int integer(JsonObject root, String key) {
        var e = root.get(key);
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber())
            throw new IllegalArgumentException("Missing arrival setting " + key);
        double n = e.getAsDouble();
        if (!Double.isFinite(n) || n != Math.rint(n) || n < 0 || n > 72000)
            throw new IllegalArgumentException("Invalid arrival setting " + key);
        return (int)n;
    }

    private static ArrivalRules load() {
        try (var stream = ArrivalRules.class.getResourceAsStream("/data/myvillage/arrival/rules.json")) {
            if (stream == null) throw new IllegalStateException("Missing arrival rules");
            try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return parse(JsonParser.parseReader(reader).getAsJsonObject());
            }
        } catch (java.io.IOException e) { throw new IllegalStateException("Cannot read arrival rules", e); }
    }
}
