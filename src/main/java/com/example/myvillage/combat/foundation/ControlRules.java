package com.example.myvillage.combat.foundation;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** 控制基础系统独立配置；不修改原有武器硬直参数。 */
public record ControlRules(int maximumTicks, int maximumTargets, int maximumSources,
        int repeatWindowTicks, double repeatFactor, double heavyHealth, double heavyFactor,
        double maximumSlow, double correctionEpsilon, double groundRetention, double airRetention, double forceEpsilon) {
    private static final class Bundled {
        private static final ControlRules INSTANCE = read();
    }

    public ControlRules {
        if (maximumTicks < 1 || maximumTargets < 1 || maximumSources < 1 || repeatWindowTicks < 1
                || !Double.isFinite(repeatFactor) || repeatFactor <= 0 || repeatFactor >= 1
                || !Double.isFinite(heavyHealth) || heavyHealth <= 0
                || !Double.isFinite(heavyFactor) || heavyFactor <= 0 || heavyFactor > 1
                || !Double.isFinite(maximumSlow) || maximumSlow <= 0 || maximumSlow >= 1
                || !Double.isFinite(correctionEpsilon) || correctionEpsilon <= 0
                || !Double.isFinite(groundRetention) || groundRetention < 0 || groundRetention >= 1
                || !Double.isFinite(airRetention) || airRetention < 0 || airRetention >= 1
                || !Double.isFinite(forceEpsilon) || forceEpsilon <= 0) {
            throw new IllegalArgumentException("控制配置参数无效");
        }
    }

    public static ControlRules get() { return Bundled.INSTANCE; }

    public static ControlRules parse(JsonObject json) {
        if (integer(json, "schema") != 1) throw new IllegalArgumentException("控制配置版本无效");
        return new ControlRules(integer(json, "maximum_ticks"), integer(json, "maximum_targets"),
                integer(json, "maximum_sources_per_target"), integer(json, "repeat_window_ticks"),
                number(json, "repeat_duration_factor"), number(json, "heavy_target_max_health"),
                number(json, "heavy_target_duration_factor"), number(json, "maximum_slow_fraction"),
                number(json, "position_correction_epsilon"), number(json, "forced_motion_ground_retention"),
                number(json, "forced_motion_air_retention"), number(json, "forced_motion_epsilon"));
    }

    /** 与原 CombatReactionMath 同样按重复次数指数衰减，并对高生命目标缩短时限。 */
    public int rootTicks(int requested, int previousRoots, double maximumHealth) {
        if (requested <= 0 || !Double.isFinite(maximumHealth) || maximumHealth <= 0) return 0;
        double duration = Math.min(requested, maximumTicks) * Math.pow(repeatFactor, Math.max(0, previousRoots));
        if (maximumHealth >= heavyHealth) duration *= heavyFactor;
        return (int) Math.round(duration);
    }

    /** 所有拒绝条件先于状态分配；纯值策略便于覆盖友军、PvP 和遮挡边界。 */
    public record TargetPolicy(boolean sourceReady, boolean targetReady, boolean sameLevel,
            boolean allied, boolean mayHarm, boolean pvpAllowed, boolean visible,
            boolean immune, boolean poised, boolean mounted) {
        public boolean allowed() {
            return sourceReady && targetReady && sameLevel && !allied && mayHarm && pvpAllowed
                    && visible && !immune && !poised && !mounted;
        }
    }

    private static ControlRules read() {
        try (var stream = ControlRules.class.getResourceAsStream("/data/myvillage/combat/foundation/control.json")) {
            if (stream == null) throw new IllegalStateException("缺少控制配置");
            return parse(JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject());
        } catch (Exception failure) {
            throw new IllegalStateException("无法读取控制配置", failure);
        }
    }

    private static double number(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonPrimitive() || !json.getAsJsonPrimitive(key).isNumber())
            throw new IllegalArgumentException("控制配置缺少数字：" + key);
        double value = json.get(key).getAsDouble();
        if (!Double.isFinite(value)) throw new IllegalArgumentException("控制配置数字无效：" + key);
        return value;
    }

    private static int integer(JsonObject json, String key) {
        double value = number(json, key);
        if (value != Math.rint(value) || value > Integer.MAX_VALUE || value < Integer.MIN_VALUE)
            throw new IllegalArgumentException("控制配置缺少整数：" + key);
        return (int) value;
    }
}
