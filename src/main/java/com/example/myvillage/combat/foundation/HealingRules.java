package com.example.myvillage.combat.foundation;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** 治疗基础规则的唯一配置来源；启动读取，不承诺 /reload 热更新。 */
public record HealingRules(int maximumTrackedEntities, int maximumDamageReceipts,
        int maximumSourcesPerEntity, int maximumDurationTicks, double maximumBonusFraction,
        double maximumReductionFraction, double maximumHealingMultiplier,
        double maximumHealingPerCall, int lifestealWindowTicks) {
    private static final String RESOURCE = "/data/myvillage/combat/foundation/healing.json";

    public HealingRules {
        if (maximumTrackedEntities < 1 || maximumTrackedEntities > 65536
                || maximumDamageReceipts < 1 || maximumDamageReceipts > 65536
                || maximumSourcesPerEntity < 1 || maximumSourcesPerEntity > 128
                || maximumDurationTicks < 1 || maximumDurationTicks > 1728000
                || !finiteBetween(maximumBonusFraction, 0, 16)
                || !finiteBetween(maximumReductionFraction, 0, 1)
                || !finiteBetween(maximumHealingMultiplier, 1, 32)
                || !finiteBetween(maximumHealingPerCall, 1, Float.MAX_VALUE)
                || lifestealWindowTicks != 20)
            throw new IllegalArgumentException("治疗配置超出安全范围");
    }

    private static class Holder { private static final HealingRules VALUE = load(); }
    public static HealingRules get() { return Holder.VALUE; }

    private static HealingRules load() {
        try (var in = HealingRules.class.getResourceAsStream(RESOURCE)) {
            if (in == null) throw new IllegalStateException("缺少治疗基础配置：" + RESOURCE);
            return parse(JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject());
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("无法读取治疗基础配置", exception);
        }
    }

    public static HealingRules parse(JsonObject json) {
        if (integer(json, "schema") != 1) throw new IllegalArgumentException("不支持的治疗配置版本");
        return new HealingRules(integer(json, "maximum_tracked_entities"), integer(json, "maximum_damage_receipts"),
                integer(json, "maximum_sources_per_entity"), integer(json, "maximum_duration_ticks"),
                number(json, "maximum_bonus_fraction"), number(json, "maximum_reduction_fraction"),
                number(json, "maximum_healing_multiplier"), number(json, "maximum_healing_per_call"),
                integer(json, "lifesteal_window_ticks"));
    }

    private static int integer(JsonObject json, String key) {
        double value = number(json, key);
        if (value != Math.rint(value) || value < Integer.MIN_VALUE || value > Integer.MAX_VALUE)
            throw new IllegalArgumentException("治疗配置必须为整数：" + key);
        return (int) value;
    }

    private static double number(JsonObject json, String key) {
        if (json == null || !json.has(key) || !json.get(key).isJsonPrimitive()
                || !json.getAsJsonPrimitive(key).isNumber())
            throw new IllegalArgumentException("治疗配置必须为数值：" + key);
        double value = json.get(key).getAsDouble();
        if (!Double.isFinite(value)) throw new IllegalArgumentException("治疗配置必须为有限值：" + key);
        return value;
    }

    private static boolean finiteBetween(double value, double minimum, double maximum) {
        return Double.isFinite(value) && value >= minimum && value <= maximum;
    }
}
