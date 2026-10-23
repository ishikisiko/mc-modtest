package com.example.myvillage.combat.foundation;

import java.util.Map;
import java.util.TreeMap;

/** 同源覆盖、异源相加的有界暂态增减疗；按来源排序使计算与插入顺序无关。 */
public final class HealingModifiers {
    private final Map<String, Modifier> sources = new TreeMap<>();

    public boolean modifier(String source, double fraction, int durationTicks, long now, HealingRules rules) {
        if (source == null || source.isBlank() || now < 0 || !Double.isFinite(fraction)
                || fraction < -rules.maximumReductionFraction() || fraction > rules.maximumBonusFraction()
                || durationTicks < 1 || durationTicks > rules.maximumDurationTicks()
                || now > Long.MAX_VALUE - durationTicks) return false;
        expire(now);
        if (!sources.containsKey(source) && sources.size() >= rules.maximumSourcesPerEntity()) return false;
        sources.put(source, new Modifier(fraction, now + durationTicks));
        return true;
    }

    public double multiplier(long now, HealingRules rules) {
        if (now < 0) return 1;
        expire(now);
        double total = 1;
        for (Modifier modifier : sources.values()) total += modifier.fraction();
        return Math.clamp(total, 0, rules.maximumHealingMultiplier());
    }

    public void clear(String source) { sources.remove(source); }
    public void expire(long now) { sources.values().removeIf(modifier -> now >= modifier.expiresAt()); }
    public boolean empty() { return sources.isEmpty(); }
    public int size() { return sources.size(); }
    private record Modifier(double fraction, long expiresAt) { }
}
