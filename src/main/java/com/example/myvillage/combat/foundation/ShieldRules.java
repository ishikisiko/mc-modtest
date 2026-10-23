package com.example.myvillage.combat.foundation;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** 暂态护盾的独立作者配置；边界值不取自客户端或玩家存档。 */
public record ShieldRules(int maxSources, float maxPerSource, float maxTotal,
                          int maxDurationTicks, int maxTrackedEntities) {
    public ShieldRules {
        if (maxSources < 1 || maxSources > 128 || !Float.isFinite(maxPerSource) || maxPerSource <= 0
                || !Float.isFinite(maxTotal) || maxTotal <= 0 || maxPerSource > maxTotal
                || maxDurationTicks < 1 || maxDurationTicks > 1_728_000
                || maxTrackedEntities < 1 || maxTrackedEntities > 65_536)
            throw new IllegalArgumentException("护盾配置超出安全边界");
    }

    private static final class Holder { private static final ShieldRules VALUE = load(); }

    public static ShieldRules get() { return Holder.VALUE; }

    private static ShieldRules load() {
        try (var in = ShieldRules.class.getResourceAsStream("/data/myvillage/combat/foundation/shields.json")) {
            if (in == null) throw new IllegalStateException("缺少护盾配置 shields.json");
            return parse(JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject());
        } catch (IOException ex) {
            throw new IllegalStateException("无法读取护盾配置", ex);
        }
    }

    public static ShieldRules parse(JsonObject json) {
        if (integer(json, "schema") != 1) throw new IllegalArgumentException("不支持的护盾配置版本");
        return new ShieldRules(integer(json, "max_sources"), number(json, "max_per_source"),
                number(json, "max_total"), integer(json, "max_duration_ticks"),
                integer(json, "max_tracked_entities"));
    }

    private static int integer(JsonObject json, String key) {
        double value = raw(json, key);
        if (value != Math.rint(value) || value < 1 || value > Integer.MAX_VALUE)
            throw new IllegalArgumentException("护盾配置必须是正整数：" + key);
        return (int) value;
    }

    private static float number(JsonObject json, String key) {
        double value = raw(json, key);
        if (value <= 0 || value > Float.MAX_VALUE || (float) value <= 0)
            throw new IllegalArgumentException("护盾配置必须是有限正数：" + key);
        return (float) value;
    }

    private static double raw(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonPrimitive() || !json.getAsJsonPrimitive(key).isNumber())
            throw new IllegalArgumentException("护盾配置缺少数值：" + key);
        double value = json.get(key).getAsDouble();
        if (!Double.isFinite(value)) throw new IllegalArgumentException("护盾配置不是有限数值：" + key);
        return value;
    }
}
