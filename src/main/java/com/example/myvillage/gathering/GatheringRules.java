package com.example.myvillage.gathering;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/** Bundled single source for layout, economics and visual timing. */
public record GatheringRules(int radius, int innerRadius, int bonusBasisPoints, int chargesPerStone,
                             int checkIntervalTicks, int activeWindowTicks,
                             double ringRadius, double ringWidth, int flowPeriodTicks) {
    public static GatheringRules parse(JsonObject json) {
        if (!json.keySet().equals(Set.of("version", "radius", "inner_radius", "bonus_basis_points",
                "charges_per_stone", "check_interval_ticks", "active_window_ticks", "ring_radius",
                "ring_width", "flow_period_ticks"))) throw new IllegalArgumentException("聚灵阵配置字段不完整");
        integer(json, "version", 1, 1);
        int radius = integer(json, "radius", 3, 3);
        int inner = integer(json, "inner_radius", 1, 1);
        return new GatheringRules(radius, inner, integer(json, "bonus_basis_points", 1, 10000),
                integer(json, "charges_per_stone", 1, 1000), integer(json, "check_interval_ticks", 1, 20),
                integer(json, "active_window_ticks", 10, 100), decimal(json, "ring_radius", 2, 3),
                decimal(json, "ring_width", .005, .1), integer(json, "flow_period_ticks", 10, 200));
    }
    private static int integer(JsonObject json, String key, int min, int max) {
        int value = json.get(key).getAsBigDecimal().intValueExact();
        if (value < min || value > max) throw new IllegalArgumentException(key);
        return value;
    }
    private static double decimal(JsonObject json, String key, double min, double max) {
        double value = json.get(key).getAsDouble();
        if (!Double.isFinite(value) || value < min || value > max) throw new IllegalArgumentException(key);
        return value;
    }
    private static class Holder {
        private static final GatheringRules INSTANCE = load();
    }
    public static GatheringRules get() { return Holder.INSTANCE; }
    private static GatheringRules load() {
        try (var stream = GatheringRules.class.getResourceAsStream("/data/myvillage/gathering/rules.json")) {
            if (stream == null) throw new IllegalStateException("Missing gathering rules");
            return parse(JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject());
        } catch (java.io.IOException e) { throw new IllegalStateException(e); }
    }
}
