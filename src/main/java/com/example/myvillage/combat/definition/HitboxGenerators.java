package com.example.myvillage.combat.definition;

import java.util.ArrayList;
import java.util.List;

/**
 * Hitbox sample generators a style file can name instead of listing samples. Each samples one
 * swept capsule per tick from {@code startTick} to {@code endTick} (the move's active window) in
 * attacker-local space (+Y up, +Z forward, see {@code CombatGeometry}). The fixed constants are the shipped
 * shapes'; a style varies only the parameters.
 */
public final class HitboxGenerators {
    private HitboxGenerators() {
    }

    /** A straight thrust from the chest whose reach grows from {@code firstRange} to {@code finalRange}. */
    public static List<HitboxSample> thrust(
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

    /** A level arc at {@code height} from {@code startAngle} to {@code endAngle} degrees (0 = straight ahead, +X positive). */
    public static List<HitboxSample> arc(
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

    /** A diagonal cut across the body, rising from low to high or descending from high to low. */
    public static List<HitboxSample> diagonal(
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
}
