package com.example.myvillage.combat.runtime;

import java.util.HashMap;
import java.util.Map;

/** 同强度刷新期限，不同强度分别到期；读取时只取仍有效的最大值。 */
public final class StrongestTimedEffect {
    private final Map<Double, Long> deadlines = new HashMap<>();

    public boolean apply(double fraction, int ticks, long now, int maximumSources) {
        expire(now);
        if (!Double.isFinite(fraction) || fraction <= 0 || fraction > 1 || ticks <= 0
                || now < 0 || now > Long.MAX_VALUE - ticks || maximumSources < 1
                || !deadlines.containsKey(fraction) && deadlines.size() >= maximumSources) return false;
        deadlines.merge(fraction, now + ticks, Math::max);
        return true;
    }

    public double strongest(long now) {
        expire(now);
        return deadlines.keySet().stream().mapToDouble(Double::doubleValue).max().orElse(0);
    }

    private void expire(long now) { deadlines.values().removeIf(until -> until <= now); }
}
