package com.example.myvillage.cultivation.fate;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;

/** 灵体节奏与识别配置；属性倍率仍由同步的天命定义拥有。 */
public record SpiritBodyRules(Adaptation adaptation, Phoenix phoenix,
        Map<String, String> styleCategories, Map<String, String> entityCategories) {
    public record Adaptation(double capacity, double healthFractionPerPoint, double maximumPointsPerAction,
            double defenseFractionPerPoint, int combatTimeoutTicks, int decayTicks,
            int actionInactivityTicks, int maximumTrackedActions) {
        public Adaptation {
            range(capacity, 1, 1000); range(healthFractionPerPoint, 0.0001, 1);
            range(maximumPointsPerAction, 0.01, capacity); range(defenseFractionPerPoint, 0.0001, 1);
            positive(combatTimeoutTicks, 24000); positive(decayTicks, 24000);
            positive(actionInactivityTicks, 24000); positive(maximumTrackedActions, 1024);
        }
    }
    public record Phoenix(double capacity, double initialReserve, double rebirthCost,
            double naturalPerSecond, double warmPerSecond, int warmRadius, double healingCostPerSecond,
            double healingHealthFractionPerSeed, int emberTicks, int protectionTicks,
            int rebirthProtectionTicks, double emberHealthFraction, double extraEmberHealthFractionPerSeed,
            double rebirthHealthFraction, double waterLossFractionPerSecond, double movementBlocksPerSecond,
            double movementAttributeFactor, double maximumMovement, double pushRadius, double pushStrength, int maximumOfflineEmbers,
            int particleIntervalTicks, int particlesPerPulse, int syncIntervalTicks) {
        public Phoenix {
            range(capacity, 1, 10000); range(initialReserve, 0, capacity); range(rebirthCost, 0.001, capacity);
            range(naturalPerSecond, 0, capacity); range(warmPerSecond, naturalPerSecond, capacity);
            positive(warmRadius, 8); range(healingCostPerSecond, 0.001, capacity);
            range(healingHealthFractionPerSeed, 0.00001, 1); positive(emberTicks, 1200);
            positive(protectionTicks, emberTicks); positive(rebirthProtectionTicks, 200);
            range(emberHealthFraction, 0.01, 1); range(extraEmberHealthFractionPerSeed, 0, 1);
            range(rebirthHealthFraction, 0.01, 1); range(waterLossFractionPerSecond, 0.001, 1);
            range(movementBlocksPerSecond, 0, 4); range(movementAttributeFactor,0.01,1); range(maximumMovement, 0, 16);
            range(pushRadius, 0, 8); range(pushStrength, 0, 2); positive(maximumOfflineEmbers, 256);
            positive(particleIntervalTicks, 100); positive(particlesPerPulse, 32); positive(syncIntervalTicks, 100);
            if (emberHealthFraction + (capacity - rebirthCost) * extraEmberHealthFractionPerSeed > 1)
                throw new IllegalArgumentException("余烬耐久不得超过身体生命上限");
        }
    }
    public SpiritBodyRules {
        styleCategories = Map.copyOf(styleCategories); entityCategories = Map.copyOf(entityCategories);
        for (var map : java.util.List.of(styleCategories, entityCategories)) map.forEach((id, category) -> {
            if (!id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) throw new IllegalArgumentException("非法灵体识别 ID");
            AdaptationCategory.fromId(category);
        });
    }
    private static final class Holder { private static final SpiritBodyRules VALUE = load(); }
    public static SpiritBodyRules get() { return Holder.VALUE; }
    private static SpiritBodyRules load() {
        try (var in = SpiritBodyRules.class.getResourceAsStream("/data/myvillage/fates/spirit_bodies.json")) {
            if (in == null) throw new IllegalStateException("缺少灵体配置");
            return parse(JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject());
        } catch (java.io.IOException e) { throw new IllegalStateException("读取灵体配置失败", e); }
    }
    public static SpiritBodyRules parse(JsonObject root) {
        if (integer(root, "schema") != 1) throw new IllegalArgumentException("不支持的灵体配置版本");
        var a = root.getAsJsonObject("adaptation"); var p = root.getAsJsonObject("phoenix");
        return new SpiritBodyRules(new Adaptation(number(a,"capacity"),number(a,"health_fraction_per_point"),
                number(a,"maximum_points_per_action"),number(a,"defense_fraction_per_point"),
                integer(a,"combat_timeout_ticks"),integer(a,"decay_ticks"),integer(a,"action_inactivity_ticks"),integer(a,"maximum_tracked_actions")),
                new Phoenix(number(p,"capacity"),number(p,"initial_reserve"),number(p,"rebirth_cost"),
                number(p,"natural_per_second"),number(p,"warm_per_second"),integer(p,"warm_radius"),
                number(p,"healing_cost_per_second"),number(p,"healing_health_fraction_per_seed"),integer(p,"ember_ticks"),
                integer(p,"protection_ticks"),integer(p,"rebirth_protection_ticks"),number(p,"ember_health_fraction"),
                number(p,"extra_ember_health_fraction_per_seed"),number(p,"rebirth_health_fraction"),
                number(p,"water_loss_fraction_per_second"),number(p,"movement_blocks_per_second"),number(p,"movement_attribute_factor"),number(p,"maximum_movement"),
                number(p,"push_radius"),number(p,"push_strength"),integer(p,"maximum_offline_embers"),
                integer(p,"particle_interval_ticks"),integer(p,"particles_per_pulse"),integer(p,"sync_interval_ticks")),
                categories(root.getAsJsonObject("style_categories")),categories(root.getAsJsonObject("entity_categories")));
    }
    private static Map<String,String> categories(JsonObject json) {
        var map = new TreeMap<String,String>(); json.entrySet().forEach(e -> map.put(e.getKey(),e.getValue().getAsString())); return map;
    }
    private static double number(JsonObject json, String key) {
        if (json == null || !json.has(key) || !json.get(key).isJsonPrimitive() || !json.getAsJsonPrimitive(key).isNumber())
            throw new IllegalArgumentException("缺少灵体数值：" + key);
        double value = json.get(key).getAsDouble();
        if (!Double.isFinite(value)) throw new IllegalArgumentException("灵体数值必须有限：" + key); return value;
    }
    private static int integer(JsonObject json, String key) {
        double n = number(json,key); if (n != Math.rint(n) || n < 0 || n > Integer.MAX_VALUE)
            throw new IllegalArgumentException("灵体数值必须为非负整数：" + key); return (int)n;
    }
    private static void positive(int n, int max) { if (n < 1 || n > max) throw new IllegalArgumentException("灵体计数超出范围"); }
    private static void range(double n, double min, double max) {
        if (!Double.isFinite(n) || n < min || n > max) throw new IllegalArgumentException("灵体参数超出范围");
    }
}
