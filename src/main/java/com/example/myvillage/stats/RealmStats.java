package com.example.myvillage.stats;

/** Growth is shared by players, beasts and ledger projections; no lifecycle or authority here. */
public final class RealmStats {
    private RealmStats() {}

    public record Derived(double factor, double health, double attack) {}

    public static double growth(StatsRules rules, RealmKey realm) { return rules.growth(realm); }

    public static Derived derived(StatsRules rules, RealmKey realm, double baseHealth, double baseAttack) {
        StatsRules.Limits limits = rules.limits();
        StatsRules.bounded(baseHealth, 0, limits.maxBaseHealth(), "base_health");
        if (baseHealth == 0) throw new IllegalArgumentException("base_health must be positive");
        StatsRules.bounded(baseAttack, 0, limits.maxBaseAttack(), "base_attack");
        double factor = rules.growth(realm);
        double health = StatsRules.bounded(baseHealth * factor, 0, limits.maxHealth(), "derived.health");
        double attack = StatsRules.bounded(baseAttack * factor, 0, limits.maxAttack(), "derived.attack");
        return new Derived(factor, health, attack);
    }
}
