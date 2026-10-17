package com.example.myvillage.cultivation.fate;

import java.util.TreeMap;

/** One transient owner/target ledger. Numeric effects are supplied by the synchronized definition. */
public final class FireSpiritTarget {
    public static final int MAX_BURN_BUCKETS = 192;
    private final TreeMap<Long, Double> burns = new TreeMap<>();
    private int hits;
    private double pendingBurst;

    /** Absorption/shields and overkill never enter the burn basis; bonus retaliation is excluded upstream. */
    public static double primaryLoss(double before, double after, double primaryFraction) {
        if (!Double.isFinite(before) || before <= 0 || !Double.isFinite(after)
                || !Double.isFinite(primaryFraction) || primaryFraction <= 0 || primaryFraction > 1) return 0;
        return Math.min(before, Math.max(0, before - Math.max(0, after))) * primaryFraction;
    }

    /** Definition-owned intervals; the final deadline pays the exact remaining damage. */
    public boolean burn(long now, double primaryHealthLoss, double fraction, int duration, int interval) {
        double total = primaryHealthLoss * fraction;
        if (now < 0 || !positive(primaryHealthLoss) || !positive(fraction) || fraction > 1
                || !positive(total) || total > Float.MAX_VALUE || duration <= 0 || duration > 24000 || interval <= 0
                || now > Long.MAX_VALUE - duration) return false;
        int count = 1 + (duration - 1) / interval;
        if (count > MAX_BURN_BUCKETS) return false;
        int newBuckets = 0;
        for (int i = 1; i <= count; i++)
            if (!burns.containsKey(now + Math.min(duration, (long) interval * i))) newBuckets++;
        // Refuse excess work rather than erase another hit's already-owned damage.
        if (burns.size() + newBuckets > MAX_BURN_BUCKETS) return false;
        double paid = 0;
        for (int i = 1; i <= count; i++) {
            double part = i == count ? total - paid : total * interval / duration;
            paid += part;
            burns.merge(now + Math.min(duration, (long) interval * i), part, FireSpiritTarget::boundedSum);
        }
        return true;
    }

    /** Hits during cooldown can bank at most one full stack; another real hit must trigger it later. */
    public boolean hit(int requiredHits, boolean cooldownReady, double weaponDamage, double multiplier) {
        if (requiredHits <= 0 || requiredHits > 64) return false;
        hits = Math.min(requiredHits, hits + 1);
        double damage = weaponDamage * multiplier;
        if (hits < requiredHits || !cooldownReady || !positive(damage) || damage > Float.MAX_VALUE) return false;
        hits = 0;
        pendingBurst = boundedSum(pendingBurst, damage);
        return true;
    }

    /** Due work is removed before hurt, so nested callbacks cannot replay it. */
    public Damage drain(long now) {
        double burn = 0;
        while (!burns.isEmpty() && burns.firstKey() <= now) burn = boundedSum(burn, burns.pollFirstEntry().getValue());
        double burst = pendingBurst;
        pendingBurst = 0;
        return new Damage(burn, burst);
    }

    public int hits() { return hits; }
    public int burnBuckets() { return burns.size(); }
    public boolean idle() { return hits == 0 && burns.isEmpty() && pendingBurst == 0; }
    private static boolean positive(double value) { return Double.isFinite(value) && value > 0; }
    private static double boundedSum(double left, double right) { return Math.min(Float.MAX_VALUE, left + right); }
    public record Damage(double burn, double burst) { }
}
