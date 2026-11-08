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
    private final Body reference;
    private final List<Body> mortal, qi, foundation, golden;
    private final Body foundationMinor, goldenMinor, nascentBreakthrough, nascentMinor, spiritBreakthrough, spiritMinor;
    private final int defaultFoundation, defaultGolden;
    private final double npcProdigyChance, npcUpperQualityChance;
    private final double transition, floor, maximumDefense;

    private CultivationStatsRules(JsonObject root, StatsRules.Limits limits) {
        fields(root, "schema", "reference", "mortal", "qi_refining", "foundation", "golden_core", "nascent_soul", "spirit_transformation", "npc",
                "default_foundation_quality", "default_golden_quality", "defense");
        if (root.get("schema").getAsBigDecimal().intValueExact() != 1) throw new IllegalArgumentException("未知数表版本");
        reference = body(root.get("reference"));
        if (reference.health <= 0 || reference.attack <= 0) throw new IllegalArgumentException("凡人基准必须为正");
        mortal = bodies(root.get("mortal"), 2); qi = bodies(root.get("qi_refining"), 9);
        JsonObject f = root.getAsJsonObject("foundation"), g = root.getAsJsonObject("golden_core");
        fields(f, "breakthrough", "minor_increment");
        fields(g, "breakthrough", "minor_increment");
        foundation = bodies(f.get("breakthrough"), 4); golden = bodies(g.get("breakthrough"), 3);
        foundationMinor = body(f.get("minor_increment")); goldenMinor = body(g.get("minor_increment"));
        JsonObject n = root.getAsJsonObject("nascent_soul"); fields(n, "breakthrough", "minor_increment");
        nascentBreakthrough = body(n.get("breakthrough")); nascentMinor = body(n.get("minor_increment"));
        JsonObject h = root.getAsJsonObject("spirit_transformation"); fields(h, "breakthrough", "minor_increment");
        spiritBreakthrough = body(h.get("breakthrough")); spiritMinor = body(h.get("minor_increment"));
        JsonObject npc = root.getAsJsonObject("npc"); fields(npc, "prodigy_chance", "upper_quality_chance");
        npcProdigyChance = probability(npc.get("prodigy_chance"));
        npcUpperQualityChance = probability(npc.get("upper_quality_chance"));
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
            Body b = at(new RealmKey("myvillage:spirit_transformation", 2), fQ, gQ);
            if (b.health + limits.maxBaseHealth() - reference.health > limits.maxHealth()
                    || b.attack + limits.maxBaseAttack() - reference.attack > limits.maxAttack() || b.defense > maximumDefense)
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
    public static CultivationStatsRules parse(Reader reader) { return parse(reader, StatsRules.bundled().limits()); }
    private static CultivationStatsRules parse(Reader reader, StatsRules.Limits limits) {
        try (var json = new com.google.gson.stream.JsonReader(reader)) {
            json.setLenient(false);
            var root = StatsRules.readJson(json, "progression");
            if (json.peek() != com.google.gson.stream.JsonToken.END_DOCUMENT) throw new IllegalArgumentException("数表含尾随数据");
            return new CultivationStatsRules(root.getAsJsonObject(), limits);
        } catch (IOException e) { throw new IllegalArgumentException("数表读取失败", e); }
    }
    /** 供StatsRules初始化期间验证实际加法上限，传入本次规则的边界以避免循环初始化。 */
    static void validateBundledEnvelope(StatsRules.Limits limits) {
        try (var input = CultivationStatsRules.class.getResourceAsStream(RESOURCE)) {
            if (input == null) throw new IllegalStateException("缺少加法数表");
            parse(new InputStreamReader(input, StandardCharsets.UTF_8), limits);
        } catch (IOException ex) { throw new IllegalStateException(ex); }
    }
    public boolean supports(RealmKey key) {
        return Set.of("myvillage:mortal", "myvillage:qi_refining", "myvillage:foundation_establishment", "myvillage:golden_core", "myvillage:nascent_soul", "myvillage:spirit_transformation").contains(key.realmId());
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
            case "myvillage:nascent_soul" -> at(new RealmKey("myvillage:golden_core", 2), foundationQuality, goldenQuality)
                    .plus(nascentBreakthrough).plus(nascentMinor.times(stage));
            case "myvillage:spirit_transformation" -> at(new RealmKey("myvillage:nascent_soul", 2), foundationQuality, goldenQuality)
                    .plus(spiritBreakthrough).plus(spiritMinor.times(stage));
            default -> throw new IllegalArgumentException("此境界尚无已确认的加法数表：" + key.realmId());
        };
    }
    public Body reference() { return reference; }
    public double maximumHealth() { return StatsRules.bundled().limits().maxHealth(); }
    public Body foundationBonus(int q) { return foundation.get(quality(q == 0 ? defaultFoundation : q, 4) - 1); }
    public Body goldenBonus(int q) { return golden.get(quality(q == 0 ? defaultGolden : q, 3) - 1); }
    public double transition() { return transition; }
    public double floor() { return floor; }
    public double maximumDefense() { return maximumDefense; }
    public int defaultFoundation() { return defaultFoundation; }
    public int defaultGolden() { return defaultGolden; }
    public double npcProdigyChance() { return npcProdigyChance; }
    public double npcUpperQualityChance() { return npcUpperQualityChance; }
    private static double probability(JsonElement value) {
        double p = value.getAsDouble();
        if (!Double.isFinite(p) || p < 0 || p > 1) throw new IllegalArgumentException("NPC 概率必须在 0..1 内");
        return p;
    }
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
    private static void increasing(Body a, Body b) {
        if (b.health < a.health || b.attack < a.attack || b.defense < a.defense || b.power < a.power || b.equals(a))
            throw new IllegalArgumentException("成长数表必须逐项不降且有提升");
    }
    private static void fields(JsonObject o, String... keys) {
        if (!o.keySet().equals(Set.of(keys))) throw new IllegalArgumentException("数表字段不符：" + o.keySet());
    }
}
