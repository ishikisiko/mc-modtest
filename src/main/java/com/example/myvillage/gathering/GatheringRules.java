package com.example.myvillage.gathering;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Single bundled authority for footprints, fuel lifetimes and per-array/per-fuel bonuses. */
public record GatheringRules(Map<GatheringTier, Tier> tiers, Map<GatheringFuel, Integer> fuelCharges,
                             int checkIntervalTicks, int activeWindowTicks, double ringWidth, int flowPeriodTicks) {
    public record Tier(int radius, int innerRadius, double ringRadius, Map<GatheringFuel, Integer> bonuses) {
        public Tier { bonuses = Map.copyOf(bonuses); }
        public int bonus(GatheringFuel fuel) { return fuel == null ? 0 : bonuses.getOrDefault(fuel, 0); }
    }
    public GatheringRules { tiers = Map.copyOf(tiers); fuelCharges = Map.copyOf(fuelCharges); }
    public Tier tier(GatheringTier tier) { return tiers.get(tier); }
    public int charges(GatheringFuel fuel) { return fuel == null ? 0 : fuelCharges.getOrDefault(fuel, 0); }
    public int maxInnerRadius() { return tiers.values().stream().mapToInt(Tier::innerRadius).max().orElseThrow(); }
    // Original low-grade consumers keep their default accessors.
    public int radius() { return tier(GatheringTier.LOW).radius(); }
    public int innerRadius() { return tier(GatheringTier.LOW).innerRadius(); }
    public double ringRadius() { return tier(GatheringTier.LOW).ringRadius(); }
    public int bonusBasisPoints() { return tier(GatheringTier.LOW).bonus(GatheringFuel.LOW); }
    public int chargesPerStone() { return charges(GatheringFuel.LOW); }
    public static GatheringRules parse(JsonObject json) {
        keys(json, Set.of("version", "tiers", "fuel_charges", "check_interval_ticks", "active_window_ticks", "ring_width", "flow_period_ticks"));
        integer(json, "version", 2, 2);
        var fuels = json.getAsJsonObject("fuel_charges");
        keys(fuels, Set.of("low_grade", "medium_grade", "high_grade"));
        var charges = new EnumMap<GatheringFuel, Integer>(GatheringFuel.class);
        int previous = 0;
        for (var fuel : GatheringFuel.values()) {
            int amount = integer(fuels, fuel.id, 1, Short.MAX_VALUE);
            if (amount <= previous) throw new IllegalArgumentException("Fuel lifetimes must increase");
            charges.put(fuel, amount); previous = amount;
        }
        var rows = json.getAsJsonObject("tiers"); keys(rows, Set.of("low", "medium", "high"));
        var tiers = new EnumMap<GatheringTier, Tier>(GatheringTier.class);
        int previousRadius = 0;
        for (var grade : GatheringTier.values()) {
            var row = rows.getAsJsonObject(grade.id);
            keys(row, Set.of("radius", "inner_radius", "ring_radius", "bonuses"));
            int radius = integer(row, "radius", 3, 5), inner = integer(row, "inner_radius", 1, radius - 1);
            if (radius <= previousRadius) throw new IllegalArgumentException("Tier footprints must increase");
            previousRadius = radius;
            var bonusJson = row.getAsJsonObject("bonuses");
            Set<String> expected = new HashSet<>();
            var bonuses = new EnumMap<GatheringFuel, Integer>(GatheringFuel.class);
            for (var fuel : GatheringFuel.values()) if (fuel.ordinal() <= grade.ordinal()) {
                expected.add(fuel.id); bonuses.put(fuel, integer(bonusJson, fuel.id, 1, 10000));
            }
            keys(bonusJson, expected);
            tiers.put(grade, new Tier(radius, inner, decimal(row, "ring_radius", inner, radius), bonuses));
        }
        return new GatheringRules(tiers, charges, integer(json, "check_interval_ticks", 1, 20),
                integer(json, "active_window_ticks", 10, 100), decimal(json, "ring_width", .005, .1),
                integer(json, "flow_period_ticks", 10, 200));
    }
    private static void keys(JsonObject json, Set<String> expected) {
        if (json == null || !json.keySet().equals(expected)) throw new IllegalArgumentException("聚灵阵配置字段不完整或包含未知字段");
    }
    private static int integer(JsonObject json, String key, int min, int max) {
        if (json == null || !json.has(key)) throw new IllegalArgumentException(key);
        int value = json.get(key).getAsBigDecimal().intValueExact();
        if (value < min || value > max) throw new IllegalArgumentException(key);
        return value;
    }
    private static double decimal(JsonObject json, String key, double min, double max) {
        double value = json.get(key).getAsDouble();
        if (!Double.isFinite(value) || value < min || value > max) throw new IllegalArgumentException(key);
        return value;
    }
    private static class Holder { private static final GatheringRules INSTANCE = load(); }
    public static GatheringRules get() { return Holder.INSTANCE; }
    private static GatheringRules load() {
        try (var stream = GatheringRules.class.getResourceAsStream("/data/myvillage/gathering/rules.json")) {
            if (stream == null) throw new IllegalStateException("Missing gathering rules");
            return parse(JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject());
        } catch (java.io.IOException e) { throw new IllegalStateException(e); }
    }
}
