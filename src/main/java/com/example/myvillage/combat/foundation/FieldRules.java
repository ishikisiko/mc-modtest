package com.example.myvillage.combat.foundation;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** 调参只来自独立 authored JSON；Java 上限仅用于拒绝损坏配置。 */
public final class FieldRules {
    private static final class Bundled { private static final FieldRules VALUE = read(); }
    private final JsonObject document;
    private final int maximumInstances, maximumPerSource, maximumTargets, maximumLifetime, minimumPulse;
    private final int maximumEffectTicks, particleInterval, particlesPerField, particlesPerTick;
    private final double maximumRadius, maximumHeal;

    private FieldRules(JsonObject input) {
        document = input.deepCopy();
        if (integer(input, "schema", 1, 1) != 1) throw new IllegalArgumentException("领域配置版本无效");
        var limits = input.getAsJsonObject("limits");
        maximumInstances = integer(limits, "maximum_instances", 1, 1024);
        maximumPerSource = integer(limits, "maximum_instances_per_source", 1, maximumInstances);
        maximumTargets = integer(limits, "maximum_targets_per_instance", 1, 256);
        maximumLifetime = integer(limits, "maximum_lifetime_ticks", 1, 72000);
        minimumPulse = integer(limits, "minimum_pulse_interval_ticks", 1, maximumLifetime);
        maximumEffectTicks = integer(limits, "maximum_effect_ticks", 1, 72000);
        maximumRadius = number(limits, "maximum_radius", .01, 64);
        maximumHeal = number(limits, "maximum_heal_per_pulse", .01, 1000000);
        var visual = input.getAsJsonObject("particles");
        particleInterval = integer(visual, "interval_ticks", 1, 1200);
        particlesPerField = integer(visual, "maximum_per_field", 0, 16);
        particlesPerTick = integer(visual, "maximum_per_server_tick", 0, 256);
        if (!valid(woodRoot(maximumRadius))) throw new IllegalArgumentException("缠根领域配置无效");
    }

    public static FieldRules bundled() { return Bundled.VALUE; }
    public static FieldRules parse(JsonObject object) { return new FieldRules(object); }
    private static FieldRules read() {
        try (var in = FieldRules.class.getResourceAsStream("/data/myvillage/combat/foundation/fields.json")) {
            if (in == null) throw new IllegalArgumentException("缺少领域配置");
            return parse(JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject());
        } catch (Exception exception) { throw new IllegalStateException("领域配置读取失败", exception); }
    }
    public JsonObject document() { return document.deepCopy(); }
    public int maximumInstances() { return maximumInstances; }
    public int maximumPerSource() { return maximumPerSource; }
    public double maximumRadius() { return maximumRadius; }
    public int particleInterval() { return particleInterval; }
    public int particlesPerField() { return particlesPerField; }
    public int particlesPerTick() { return particlesPerTick; }

    public FieldSpec woodRoot(double radius) {
        var root = document.getAsJsonObject("wood_root");
        return new FieldSpec(ResourceLocation.parse("myvillage:wood_root"), ResourceLocation.parse("myvillage:wood_root"),
                FieldSpec.Anchor.FOLLOW_SOURCE, null, radius,
                integer(root, "lifetime_ticks", 1, maximumLifetime),
                integer(root, "pulse_interval_ticks", minimumPulse, maximumLifetime),
                integer(root, "maximum_targets", 1, maximumTargets), FieldSpec.Effect.ROOT,
                integer(root, "root_ticks", 1, maximumEffectTicks), 0, true, FieldSpec.Targeting.ENEMIES);
    }

    public boolean valid(FieldSpec spec) {
        if (spec == null || spec.kind() == null || spec.effect() == null || spec.anchor() == null
                || spec.effectType() == null || spec.targeting() == null || !Double.isFinite(spec.radius())
                || spec.radius() <= 0 || spec.radius() > maximumRadius || spec.lifetimeTicks() < 1
                || spec.lifetimeTicks() > maximumLifetime || spec.pulseIntervalTicks() < minimumPulse
                || spec.pulseIntervalTicks() > maximumLifetime || spec.maximumTargets() < 1
                || spec.maximumTargets() > maximumTargets || !Float.isFinite(spec.magnitude())) return false;
        if (spec.anchor() == FieldSpec.Anchor.FIXED && !finite(spec.fixedCenter())) return false;
        return switch (spec.effectType()) {
            case ROOT -> spec.targeting() == FieldSpec.Targeting.ENEMIES
                    && spec.effectTicks() > 0 && spec.effectTicks() <= maximumEffectTicks && spec.magnitude() == 0;
            case SLOW -> spec.targeting() == FieldSpec.Targeting.ENEMIES
                    && spec.effectTicks() > 0 && spec.effectTicks() <= maximumEffectTicks
                    && spec.magnitude() > 0 && spec.magnitude() < 1;
            case HEAL -> spec.targeting() == FieldSpec.Targeting.ALLIES && spec.effectTicks() == 0
                    && spec.magnitude() > 0 && spec.magnitude() <= maximumHeal;
        };
    }

    private static boolean finite(net.minecraft.world.phys.Vec3 point) {
        return point != null && Double.isFinite(point.x) && Double.isFinite(point.y) && Double.isFinite(point.z);
    }
    private static int integer(JsonObject object, String key, int min, int max) {
        double value = number(object, key, min, max);
        if (value != Math.rint(value)) throw new IllegalArgumentException("领域整数配置无效：" + key);
        return (int) value;
    }
    private static double number(JsonObject object, String key, double min, double max) {
        if (object == null || !object.has(key) || !object.get(key).isJsonPrimitive()
                || !object.getAsJsonPrimitive(key).isNumber()) throw new IllegalArgumentException("缺少领域数值：" + key);
        double value = object.get(key).getAsDouble();
        if (!Double.isFinite(value) || value < min || value > max)
            throw new IllegalArgumentException("领域数值越界：" + key);
        return value;
    }
}
