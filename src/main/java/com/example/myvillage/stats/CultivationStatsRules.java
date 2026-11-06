package com.example.myvillage.stats;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** 当前已设计境界的加法成长；四元组始终为生命、攻击、肉身防御、灵力。 */
public final class CultivationStatsRules {
    public static final String RESOURCE = "/data/myvillage/stats/progression.json";
    public record Body(double health, double attack, double defense, long power) {
        public Body {
            if (!Double.isFinite(health) || !Double.isFinite(attack) || !Double.isFinite(defense)
                    || health < 0 || attack < 0 || defense < 0 || power < 0)
                throw new IllegalArgumentException("属性必须是有限非负数");
        }
        public Body plus(Body other) {
            return new Body(health + other.health, attack + other.attack, defense + other.defense,
                    Math.addExact(power, other.power));
        }
        public Body times(int count) {
            if (count < 0) throw new IllegalArgumentException("阶段不得为负");
            return new Body(health * count, attack * count, defense * count, Math.multiplyExact(power, count));
        }
    }
    private final boolean mockTribulation;
    private final Body reference;
    private final List<Body> mortal, qi, foundation, golden;
    private final Body foundationMinor, goldenMinor;
    private final int defaultFoundation, defaultGolden;
    private final int[] foundationHits, goldenHits;
    private final double transition, floor, maximumDefense;

    private CultivationStatsRules(JsonObject root) {
        fields(root, "schema", "reference", "mortal", "qi_refining", "foundation", "golden_core",
                "default_foundation_quality", "default_golden_quality", "defense", "mock_tribulation");
        if (root.get("schema").getAsBigDecimal().intValueExact() != 1) throw new IllegalArgumentException("未知数表版本");
        if (!root.get("mock_tribulation").isJsonPrimitive() || !root.get("mock_tribulation").getAsJsonPrimitive().isBoolean()) throw new IllegalArgumentException("MOCK 标志必须是布尔值");
        mockTribulation = root.get("mock_tribulation").getAsBoolean();
        reference = body(root.get("reference"));
        if (reference.health <= 0 || reference.attack <= 0) throw new IllegalArgumentException("凡人基准必须为正");
        mortal = bodies(root.get("mortal"), 2); qi = bodies(root.get("qi_refining"), 9);
        JsonObject f = root.getAsJsonObject("foundation"), g = root.getAsJsonObject("golden_core");
        fields(f, "breakthrough", "minor_increment", "mock_main_hits");
        fields(g, "breakthrough", "minor_increment", "mock_main_hits");
        foundation = bodies(f.get("breakthrough"), 4); golden = bodies(g.get("breakthrough"), 3);
        foundationMinor = body(f.get("minor_increment")); goldenMinor = body(g.get("minor_increment"));
        foundationHits = hits(f.getAsJsonArray("mock_main_hits"), 4);
        goldenHits = hits(g.getAsJsonArray("mock_main_hits"), 3);
        defaultFoundation = quality(root.get("default_foundation_quality").getAsInt(), 4);
        defaultGolden = quality(root.get("default_golden_quality").getAsInt(), 3);
        JsonObject d = root.getAsJsonObject("defense"); fields(d, "transition", "floor", "maximum");
        transition = d.get("transition").getAsDouble(); floor = d.get("floor").getAsDouble();
        maximumDefense = d.get("maximum").getAsDouble();
        if (!Double.isFinite(transition) || !Double.isFinite(floor) || transition <= 0 || transition >= 1
                || floor <= 0 || floor >= 1 - transition || !Double.isFinite(maximumDefense) || maximumDefense <= 0)
            throw new IllegalArgumentException("非法防御参数");
        for (int i = 1; i < qi.size(); i++) increasing(qi.get(i - 1), qi.get(i));
        for (int i = 1; i < foundation.size(); i++) increasing(foundation.get(i - 1), foundation.get(i));
        for (int i = 1; i < golden.size(); i++) increasing(golden.get(i - 1), golden.get(i));
        for (int fQ = 1; fQ <= 4; fQ++) for (int gQ = 1; gQ <= 3; gQ++) {
            Body b = at(new RealmKey("myvillage:golden_core", 2), fQ, gQ);
            var limits = StatsRules.bundled().limits();
            if (b.health > limits.maxHealth() || b.attack > limits.maxAttack() || b.defense > maximumDefense)
                throw new IllegalArgumentException("数表超过属性边界");
        }
    }
    public static CultivationStatsRules bundled() { return Holder.VALUE; }
    private static final class Holder {
        static final CultivationStatsRules VALUE = load();
        static CultivationStatsRules load() {
            try (var in = CultivationStatsRules.class.getResourceAsStream(RESOURCE)) {
                if (in == null) throw new IllegalStateException("缺少加法数表");
                return parse(new InputStreamReader(in, StandardCharsets.UTF_8));
            } catch (IOException e) { throw new IllegalStateException(e); }
        }
    }
    public static CultivationStatsRules parse(Reader reader) {
        try (var json = new com.google.gson.stream.JsonReader(reader)) {
            json.setLenient(false);
            var root = StatsRules.readJson(json, "progression");
            if (json.peek() != com.google.gson.stream.JsonToken.END_DOCUMENT) throw new IllegalArgumentException("数表含尾随数据");
            return new CultivationStatsRules(root.getAsJsonObject());
        } catch (IOException e) { throw new IllegalArgumentException("数表读取失败", e); }
    }
    public boolean supports(RealmKey key) {
        return Set.of("myvillage:mortal", "myvillage:qi_refining", "myvillage:foundation_establishment", "myvillage:golden_core").contains(key.realmId());
    }
    public Body at(RealmKey key, int foundationQuality, int goldenQuality) {
        if (foundationQuality < 0 || foundationQuality > 4 || goldenQuality < 0 || goldenQuality > 3) throw new IllegalArgumentException("非法品质");
        int stage = key.stage();
        int maximumStage = key.realmId().equals("myvillage:qi_refining") ? 9 : key.realmId().equals("myvillage:mortal") ? 2 : 3;
        if (stage >= maximumStage) throw new IllegalArgumentException("阶段超出数表");
        return switch (key.realmId()) {
            case "myvillage:mortal" -> mortal.get(stage);
            case "myvillage:qi_refining" -> qi.get(stage);
            case "myvillage:foundation_establishment" -> qi.getLast()
                    .plus(foundationBonus(foundationQuality)).plus(foundationMinor.times(stage));
            case "myvillage:golden_core" -> qi.getLast().plus(foundationBonus(foundationQuality))
                    .plus(foundationMinor.times(2)).plus(goldenBonus(goldenQuality)).plus(goldenMinor.times(stage));
            default -> throw new IllegalArgumentException("此境界尚无已确认的加法数表：" + key.realmId());
        };
    }
    public boolean mockTribulation() { return mockTribulation; }
    public Body reference() { return reference; }
    public Body foundationBonus(int q) { return foundation.get(quality(q == 0 ? defaultFoundation : q, 4) - 1); }
    public Body goldenBonus(int q) { return golden.get(quality(q == 0 ? defaultGolden : q, 3) - 1); }
    public int foundationHits(int q) { return foundationHits[quality(q, 4) - 1]; }
    public int goldenHits(int q) { return goldenHits[quality(q, 3) - 1]; }
    public double transition() { return transition; }
    public double floor() { return floor; }
    public double maximumDefense() { return maximumDefense; }
    public int defaultFoundation() { return defaultFoundation; }
    public int defaultGolden() { return defaultGolden; }
    private static int quality(int q, int maximum) {
        if (q < 1 || q > maximum) throw new IllegalArgumentException("非法品质：" + q);
        return q;
    }
    private static Body body(JsonElement value) {
        JsonArray a = value.getAsJsonArray();
        if (a.size() != 4) throw new IllegalArgumentException("属性四元组必须有四项");
        return new Body(a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble(),
                a.get(3).getAsBigDecimal().longValueExact());
    }
    private static List<Body> bodies(JsonElement value, int count) {
        JsonArray a = value.getAsJsonArray();
        if (a.size() != count) throw new IllegalArgumentException("数表长度不符");
        List<Body> result = new ArrayList<>(); a.forEach(v -> result.add(body(v))); return List.copyOf(result);
    }
    private static int[] hits(JsonArray a, int count) {
        if (a.size() != count) throw new IllegalArgumentException("mock 雷数长度不符");
        int[] result = new int[count];
        for (int i = 0; i < count; i++) {
            result[i] = a.get(i).getAsBigDecimal().intValueExact();
            if (result[i] < 0 || result[i] > 64) throw new IllegalArgumentException("非法 mock 雷数");
        }
        return result;
    }
    private static void increasing(Body a, Body b) {
        if (b.health < a.health || b.attack < a.attack || b.defense < a.defense || b.power < a.power || b.equals(a))
            throw new IllegalArgumentException("成长数表必须逐项不降且有提升");
    }
    private static void fields(JsonObject o, String... keys) {
        if (!o.keySet().equals(Set.of(keys))) throw new IllegalArgumentException("数表字段不符：" + o.keySet());
    }
}
