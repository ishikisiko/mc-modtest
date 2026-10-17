package com.example.myvillage.cultivation.fate;

/** Pure arithmetic used at the successful pill boundary and after ordinary damage mitigation. */
public final class PillAegisEffects {
    private PillAegisEffects() { }

    public static double healingAmount(String medicine, double ordinary, double bonus) {
        if (!Double.isFinite(ordinary) || ordinary < 0 || !Double.isFinite(bonus) || bonus < 0)
            throw new IllegalArgumentException("Invalid pill healing amount");
        return ordinary * ("yangyuan".equals(medicine) ? 1 + bonus : 1);
    }

    public static PillAegisState afterConsumption(PillAegisState state, long now, String medicine,
            boolean successful, double maximumHealth, double fraction, int duration, int cooldown) {
        PillAegisState current = state.expire(now);
        if (!successful || !"yangyuan".equals(medicine) || now < current.cooldownReadyAt()
                || current.shield() > 0 || !Double.isFinite(maximumHealth) || maximumHealth <= 0
                || !Double.isFinite(fraction) || fraction <= 0 || fraction > 1
                || duration <= 0 || cooldown <= 0) return current;
        double amount = maximumHealth * fraction;
        if (!Double.isFinite(amount) || amount > Float.MAX_VALUE) return current;
        return new PillAegisState(amount, Math.addExact(now, duration), Math.addExact(now, cooldown));
    }

    /** Input has already passed armor, magic and typed resistance exactly once. */
    public static Absorption absorb(PillAegisState state, long now, float damage, boolean bypass) {
        PillAegisState current = state.expire(now);
        if (bypass || !Float.isFinite(damage) || damage <= 0 || current.shield() == 0)
            return new Absorption(current, damage, 0);
        float blocked = (float) Math.min(current.shield(), damage);
        double remaining = Math.max(0, current.shield() - blocked);
        PillAegisState next = remaining == 0 ? current.clearShield()
                : new PillAegisState(remaining, current.shieldExpiresAt(), current.cooldownReadyAt());
        return new Absorption(next, Math.max(0, damage - blocked), blocked);
    }

    public record Absorption(PillAegisState next, float damage, float blocked) { }
}
