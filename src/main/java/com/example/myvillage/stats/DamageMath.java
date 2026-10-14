package com.example.myvillage.stats;

import java.util.List;
import java.util.Objects;

/** Pre-vanilla composition and post-vanilla typed resistance are separate numerical stages. */
public final class DamageMath {
    private DamageMath() {}

    public record Trace(double base, double flat, double afterFlat, double moveMultiplier,
                        double afterMove, double bonusSum, double afterBonuses,
                        double totalMultiplier, double requestedDamage) {}

    public record Mitigation(double postVanilla, double resistance, double penetration,
                             double effectiveResistance, double factor, double damage) {}

    public static Trace calculate(double base, double flat, double move, List<Double> bonuses, double total) {
        nonnegative(base, "damage.base");
        nonnegative(flat, "damage.flat");
        nonnegative(move, "damage.move");
        nonnegative(total, "damage.total");
        Objects.requireNonNull(bonuses, "damage.bonuses");
        double sum = 0;
        for (Double bonus : bonuses) sum = nonnegative(sum + nonnegative(
                Objects.requireNonNull(bonus, "damage.bonus"), "damage.bonus"), "damage.bonus_sum");
        double afterFlat = nonnegative(base + flat, "damage.after_flat");
        double afterMove = nonnegative(afterFlat * move, "damage.after_move");
        double afterBonuses = nonnegative(afterMove * (1 + sum), "damage.after_bonuses");
        double requested = nonnegative(afterBonuses * total, "damage.requested");
        return new Trace(base, flat, afterFlat, move, afterMove, sum, afterBonuses, total, requested);
    }

    public static Trace calculate(double base, double flat, double move, List<Double> bonuses,
                                  double total, StatsRules rules) {
        StatsRules.Limits limits = rules.limits();
        StatsRules.bounded(base, 0, limits.maxDamage(), "damage.base");
        StatsRules.bounded(flat, 0, limits.maxFlatDamage(), "damage.flat");
        StatsRules.bounded(move, 0, limits.maxMoveMultiplier(), "damage.move");
        StatsRules.bounded(total, 0, limits.maxTotalMultiplier(), "damage.total");
        Trace trace = calculate(base, flat, move, bonuses, total);
        StatsRules.bounded(trace.bonusSum(), 0, limits.maxBonus(), "damage.bonus_sum");
        StatsRules.bounded(trace.requestedDamage(), 0, limits.maxDamage(), "damage.requested");
        return trace;
    }

    /**
     * Accepts any finite nonnegative amount from vanilla or another mod. The outgoing
     * composition budget belongs to calculate; it cannot constrain external damage sources.
     */
    public static Mitigation mitigate(double postVanilla, double resistance, double penetration, StatsRules rules) {
        StatsRules.Limits limits = rules.limits();
        nonnegative(postVanilla, "damage.post_vanilla");
        StatsRules.bounded(resistance, limits.minResistance(), limits.maxResistance(), "damage.resistance");
        StatsRules.bounded(penetration, 0, limits.maxPenetration(), "damage.penetration");
        double effective = Math.max(limits.minResistance(), Math.min(limits.maxResistance(), resistance - penetration));
        double factor = 1 - effective;
        return new Mitigation(postVanilla, resistance, penetration, effective, factor, postVanilla * factor);
    }

    private static double nonnegative(double value, String field) {
        if (!Double.isFinite(value) || value < 0) throw new IllegalArgumentException(field + " must be finite and nonnegative");
        return value;
    }
}
