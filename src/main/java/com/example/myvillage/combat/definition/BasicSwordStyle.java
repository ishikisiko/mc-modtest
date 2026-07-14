package com.example.myvillage.combat.definition;

import com.example.myvillage.MyVillageMod;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class BasicSwordStyle {
    public static final ResourceLocation QINGFENG_SWORD_ID = id("qingfeng_sword");
    public static final ResourceLocation READY_IDLE_ANIMATION = id("sword_ready_idle");
    public static final int COMBO_TIMEOUT_TICKS = 14;
    public static final int MINIMUM_INTENT_INTERVAL_TICKS = 2;

    public static final CombatStyleDefinition DEFINITION = new CombatStyleDefinition(
            id("basic_sword"),
            Set.of(QINGFENG_SWORD_ID),
            COMBO_TIMEOUT_TICKS,
            MINIMUM_INTENT_INTERVAL_TICKS,
            List.of(
                    move(
                            "basic_sword_01_thrust", "combat.myvillage.move.basic_sword_01_thrust",
                            11, 3, 4, 0.90, 1, 3.0, 8, 0.20,
                            "center_thrust", thrustSamples(3, 4, 2.55, 2.95, 0.16), Optional.empty()),
                    move(
                            "basic_sword_02_horizontal_cut", "combat.myvillage.move.basic_sword_02_horizontal_cut",
                            13, 4, 6, 0.95, 3, 2.8, 10, 0.28,
                            "horizontal_arc_110", arcSamples(4, 6, 2.8, 55.0, -55.0, 1.15, 0.18), Optional.empty()),
                    move(
                            "basic_sword_03_rising_cut", "combat.myvillage.move.basic_sword_03_rising_cut",
                            15, 5, 7, 1.00, 2, 2.8, 12, 0.24,
                            "rising_diagonal", diagonalSamples(5, 7, false, 0.20), Optional.empty()),
                    move(
                            "basic_sword_04_diagonal_cut", "combat.myvillage.move.basic_sword_04_diagonal_cut",
                            17, 6, 8, 1.10, 3, 3.0, 14, 0.32,
                            "descending_diagonal_thick", diagonalSamples(6, 8, true, 0.28), Optional.empty()),
                    move(
                            "basic_sword_05_lunge_thrust", "combat.myvillage.move.basic_sword_05_lunge_thrust",
                            20, 7, 9, 1.25, 2, 3.5, 17, 0.36,
                            "long_lunge_thrust", thrustSamples(7, 9, 2.8, 3.5, 0.19),
                            Optional.of(new StepDefinition(6, 0.8, 0.35)))));

    private BasicSwordStyle() {
    }

    private static AttackMoveDefinition move(
            String path,
            String displayKey,
            int totalTicks,
            int activeStart,
            int activeEnd,
            double multiplier,
            int maximumTargets,
            double range,
            int bufferStart,
            double knockback,
            String shape,
            List<HitboxSample> samples,
            Optional<StepDefinition> step) {
        ResourceLocation moveId = id(path);
        return new AttackMoveDefinition(
                moveId,
                displayKey,
                totalTicks,
                activeStart,
                activeEnd,
                multiplier,
                maximumTargets,
                range,
                bufferStart,
                knockback,
                new AnimationDefinition(moveId, totalTicks),
                new HitboxDefinition(shape, samples, 0.20, 0.12),
                step);
    }

    private static List<HitboxSample> thrustSamples(
            int startTick,
            int endTick,
            double firstRange,
            double finalRange,
            double radius) {
        List<HitboxSample> samples = new ArrayList<>();
        for (int tick = startTick; tick <= endTick; tick++) {
            double progress = (double) (tick - startTick) / Math.max(1, endTick - startTick);
            double reach = firstRange + (finalRange - firstRange) * progress;
            samples.add(new HitboxSample(
                    tick, 0.0, 1.05, 0.55, 0.0, 1.20, reach, radius, 0.25));
        }
        return samples;
    }

    private static List<HitboxSample> arcSamples(
            int startTick,
            int endTick,
            double range,
            double startAngle,
            double endAngle,
            double height,
            double radius) {
        List<HitboxSample> samples = new ArrayList<>();
        for (int tick = startTick; tick <= endTick; tick++) {
            double progress = (double) (tick - startTick) / Math.max(1, endTick - startTick);
            double angle = Math.toRadians(startAngle + (endAngle - startAngle) * progress);
            samples.add(new HitboxSample(
                    tick,
                    Math.sin(angle) * 0.45,
                    height,
                    Math.cos(angle) * 0.45,
                    Math.sin(angle) * range,
                    height,
                    Math.cos(angle) * range,
                    radius,
                    0.34));
        }
        return samples;
    }

    private static List<HitboxSample> diagonalSamples(
            int startTick,
            int endTick,
            boolean descending,
            double radius) {
        List<HitboxSample> samples = new ArrayList<>();
        for (int tick = startTick; tick <= endTick; tick++) {
            double progress = (double) (tick - startTick) / Math.max(1, endTick - startTick);
            double side = -0.75 + 1.50 * progress;
            double opposite = 0.95 - 1.90 * progress;
            double low = 0.45 + 0.30 * progress;
            double high = 1.90 - 0.15 * progress;
            samples.add(descending
                    ? new HitboxSample(tick, -side, high, 0.55, -opposite, low, 2.75, radius, 0.22)
                    : new HitboxSample(tick, side, low, 0.55, opposite, high, 2.55, radius, 0.20));
        }
        return samples;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MyVillageMod.MOD_ID, path);
    }
}
