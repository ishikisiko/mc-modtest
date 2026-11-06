package com.example.myvillage.stats;

/** Growth is shared by players, beasts and ledger projections; no lifecycle or authority here. */
public final class RealmStats {
    private RealmStats() {}

    public record Derived(double factor, double health, double attack, double defense) {}

    public static double growth(StatsRules rules, RealmKey realm) { return rules.growth(realm); }

    public static Derived derived(StatsRules rules, RealmKey realm, double baseHealth, double baseAttack) {
        StatsRules.Limits limits = rules.limits();
        StatsRules.bounded(baseHealth, 0, limits.maxBaseHealth(), "base_health");
        if (baseHealth == 0) throw new IllegalArgumentException("base_health must be positive");
        StatsRules.bounded(baseAttack, 0, limits.maxBaseAttack(), "base_attack");
        var additions = CultivationStatsRules.bundled();
        if (additions.supports(realm)) {
            var body = additions.at(realm, 0, 0);
            return new Derived(1,
                    StatsRules.bounded(baseHealth + body.health() - additions.reference().health(), 0, limits.maxHealth(), "derived.health"),
                    StatsRules.bounded(baseAttack + body.attack() - additions.reference().attack(), 0, limits.maxAttack(), "derived.attack"),
                    body.defense());
        }
        // 元婴仍只有既有 NPC 数据；本轮没有新增玩家元婴阶段或擅自拟定它的品质数表。
        double factor = rules.growth(realm);
        double health = StatsRules.bounded(baseHealth * factor, 0, limits.maxHealth(), "derived.health");
        double attack = StatsRules.bounded(baseAttack * factor, 0, limits.maxAttack(), "derived.attack");
        return new Derived(factor, health, attack, 0);
    }

    /** Vanilla ravagers/giants can exceed the authored player/beast base envelope.
     * Keep their native base and saturate at the shared effective limits. */
    public static Derived vanillaDerived(StatsRules rules, RealmKey realm, double baseHealth, double baseAttack) {
        StatsRules.Limits limits = rules.limits();
        StatsRules.bounded(baseHealth, 0, limits.maxHealth(), "vanilla.base_health");
        if (baseHealth == 0) throw new IllegalArgumentException("base_health must be positive");
        StatsRules.bounded(baseAttack, 0, limits.maxAttack(), "vanilla.base_attack");
        var additions = CultivationStatsRules.bundled();
        if (additions.supports(realm)) {
            var body = additions.at(realm, 0, 0);
            return new Derived(1, Math.min(baseHealth + body.health() - additions.reference().health(), limits.maxHealth()),
                    Math.min(baseAttack + body.attack() - additions.reference().attack(), limits.maxAttack()), body.defense());
        }
        double factor = rules.growth(realm);
        return new Derived(factor, Math.min(baseHealth * factor, limits.maxHealth()),
                Math.min(baseAttack * factor, limits.maxAttack()), 0);
    }
}
