package com.example.myvillage.spiritstone;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Bundled values are the single source for bag capacity and collection frequency. */
public record SpiritStoneBagRules(int slots, int collectionIntervalTicks) {
    public SpiritStoneBagRules {
        if (slots < 1 || slots > 256 || collectionIntervalTicks < 1 || collectionIntervalTicks > 1200)
            throw new IllegalArgumentException("Invalid spirit stone bag rules");
    }
    private static final class Holder {
        private static final SpiritStoneBagRules INSTANCE = load();
    }
    public static SpiritStoneBagRules get() { return Holder.INSTANCE; }
    private static SpiritStoneBagRules load() {
        try (var stream = SpiritStoneBagRules.class.getResourceAsStream("/data/myvillage/spirit_stones/bag.json")) {
            if (stream == null) throw new IllegalStateException("Missing spirit stone bag rules");
            var data = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            if (!data.keySet().equals(java.util.Set.of("slots", "collection_interval_ticks")))
                throw new IllegalArgumentException("Unknown spirit stone bag rule");
            return new SpiritStoneBagRules(data.get("slots").getAsBigDecimal().intValueExact(),
                    data.get("collection_interval_ticks").getAsBigDecimal().intValueExact());
        } catch (java.io.IOException e) { throw new IllegalStateException("Cannot load spirit stone bag rules", e); }
    }
}
