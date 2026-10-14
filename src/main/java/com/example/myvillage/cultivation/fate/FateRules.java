package com.example.myvillage.cultivation.fate;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Startup-only roll rules, not a clock or client seed. Effect values live in synced definitions. */
public record FateRules(List<Double> tierWeights, int requestTicks) {
    public FateRules {
        tierWeights = List.copyOf(tierWeights);
        if (tierWeights.size() != 6 || tierWeights.stream().anyMatch(v -> !Double.isFinite(v) || v < 0)
                || tierWeights.stream().mapToDouble(Double::doubleValue).sum() <= 0 || requestTicks < 1)
            throw new IllegalArgumentException("Invalid fate rules");
    }
    private static final class Holder { static final FateRules VALUE = load(); }
    public static FateRules bundled() { return Holder.VALUE; }
    private static FateRules load() {
        try (var in = FateRules.class.getResourceAsStream("/data/myvillage/fates/rules.json")) {
            if (in == null) throw new IllegalStateException("Missing fate rules");
            var json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            return new FateRules(java.util.stream.StreamSupport.stream(json.getAsJsonArray("tier_weights").spliterator(), false)
                    .map(e -> e.getAsDouble()).toList(), json.get("request_ticks").getAsInt());
        } catch (java.io.IOException e) { throw new IllegalStateException(e); }
    }
}
