package com.example.myvillage.sim.engine;

import com.example.myvillage.sim.cultivation.SpiritualPowerMath;
import com.example.myvillage.sim.model.Person;
import com.example.myvillage.sim.combat.NpcCombatProfile;
import com.example.myvillage.stats.CultivationStatsRules;
import com.example.myvillage.stats.RealmKey;

/** 命簿拥有身份、品质和当前灵力；化身不会重新抽取或补满。 */
public final class NpcCultivation {
    private NpcCultivation() { }

    public static void initialize(SimContext ctx, Person p) {
        p.prodigy = ctx.rng(p.id, Purpose.NPC_PRODIGY).chance(CultivationStatsRules.bundled().npcProdigyChance());
        reached(ctx, p);
        p.spiritualPower = body(ctx, p).power();
    }

    public static CultivationStatsRules.Body body(SimContext ctx, Person p) {
        return CultivationStatsRules.bundled().at(key(ctx, p), p.foundationQuality, p.goldenCoreQuality);
    }

    public static RealmKey key(SimContext ctx, Person p) {
        String realm = ctx.realm(p).id();
        return new RealmKey(realm.indexOf(':') < 0 ? "myvillage:" + realm : realm, p.stage);
    }

    private static int quality(SimContext ctx, Person p, int purpose) {
        return !p.prodigy ? 1 : ctx.rng(p.id, purpose).chance(CultivationStatsRules.bundled().npcUpperQualityChance()) ? 3 : 2;
    }

    /** 只在成功达到阶段时补上尚未确定的品质，小阶段与刷新不重抽。 */
    public static void reached(SimContext ctx, Person p) {
        if (p.realm < ctx.realms.indexOf("foundation_establishment")) p.foundationQuality = 0;
        if (p.realm < ctx.realms.indexOf("golden_core")) p.goldenCoreQuality = 0;
        if (p.realm >= ctx.realms.indexOf("foundation_establishment") && p.foundationQuality == 0)
            p.foundationQuality = quality(ctx, p, Purpose.NPC_FOUNDATION_QUALITY);
        if (p.realm >= ctx.realms.indexOf("golden_core") && p.goldenCoreQuality == 0)
            p.goldenCoreQuality = quality(ctx, p, Purpose.NPC_GOLDEN_QUALITY);
        var body = body(ctx, p);
        p.spiritualPower = SpiritualPowerMath.clamp(p.spiritualPower, body.power());
    }

    /** 境界只增加自身贡献，战斗档案中的装备生命贡献仍然保留。 */
    public static void advanced(SimContext ctx, Person p, CultivationStatsRules.Body previous) {
        reached(ctx, p);
        var old = ctx.state.combat.profiles.get(p.id);
        if (old == null) return;
        double maximum = Math.min(CultivationStatsRules.bundled().maximumHealth(),
                old.maximumHealth() + body(ctx, p).health() - previous.health());
        if (maximum != old.maximumHealth()) ctx.state.combat.profiles.put(p.id,
                new NpcCombatProfile(Math.min(old.health(), maximum), maximum, old.weapon(), old.recoveryUntil(),
                        Math.incrementExact(old.revision()), old.lastAttacker(), old.lastDamageTick(), old.body()));
    }

    public static boolean spend(SimContext ctx, int id, long cost) {
        if (cost < 0) throw new IllegalArgumentException("灵力消耗不能为负");
        Person p = ctx.state.persons.get(id);
        if (p == null) return false;
        var next = SpiritualPowerMath.spend(p.spiritualPower, body(ctx, p).power(), cost);
        if (next.isEmpty()) return false;
        p.spiritualPower = next.getAsLong();
        return true;
    }

    public static boolean recover(SimContext ctx, int id, long amount) {
        if (amount < 0) throw new IllegalArgumentException("灵力恢复不能为负");
        Person p = ctx.state.persons.get(id);
        if (p == null) return false;
        long next = SpiritualPowerMath.recover(p.spiritualPower, body(ctx, p).power(), amount);
        if (next == p.spiritualPower) return false;
        p.spiritualPower = next;
        return true;
    }

    public static int recoveryFactor(SimContext ctx, int id) {
        Person p = ctx.state.persons.get(id);
        if (p == null || ctx.technique(p) == null) return 10_000;
        return (int) Math.min(Integer.MAX_VALUE, Math.round(Cultivation.techniqueFactor(ctx, p, true) * 10_000));
    }

    public static void validate(com.example.myvillage.sim.data.RealmTable realms, Person p) {
        boolean foundation = p.realm >= realms.indexOf("foundation_establishment");
        boolean golden = p.realm >= realms.indexOf("golden_core");
        if (!(foundation ? validQuality(p.prodigy, p.foundationQuality) : p.foundationQuality == 0)
                || !(golden ? validQuality(p.prodigy, p.goldenCoreQuality) : p.goldenCoreQuality == 0))
            throw new com.example.myvillage.sim.SimFormatException("person " + p.id + " has invalid cultivation qualities");
        long cap = CultivationStatsRules.bundled().at(new RealmKey("myvillage:" + realms.get(p.realm).id(), p.stage),
                p.foundationQuality, p.goldenCoreQuality).power();
        if (p.spiritualPower < 0 || p.spiritualPower > cap)
            throw new com.example.myvillage.sim.SimFormatException("person " + p.id + " has invalid spiritual power");
    }
    private static boolean validQuality(boolean prodigy, int quality) {
        return prodigy ? quality == 2 || quality == 3 : quality == 1;
    }
}
