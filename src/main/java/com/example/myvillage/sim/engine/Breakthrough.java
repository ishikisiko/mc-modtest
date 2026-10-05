package com.example.myvillage.sim.engine;

import com.example.myvillage.sim.SimRng;
import com.example.myvillage.sim.data.RealmTable;
import com.example.myvillage.sim.data.Rules;
import com.example.myvillage.sim.model.Person;
import com.example.myvillage.sim.model.Sect;
import java.util.ArrayList;
import java.util.List;

/**
 * Great-realm breakthroughs (design §4.2). At the cap of a realm's last stage a person attempts
 * now and then, or desperately when lifespan is nearly out. The chance comes from root, technique,
 * age, earlier failures, old injuries, master and a sect pill. Failure costs progress and leaves an
 * injury, and sometimes kills (走火入魔). When a sect pill tipped a success, or an old wound a death,
 * the text says so.
 */
public final class Breakthrough {
    private Breakthrough() {
    }

    public static void daily(SimContext ctx, Person p) {
        if (p.realm + 1 >= ctx.realms.size() || !Cultivation.atRealmCap(ctx, p)) {
            return;
        }
        Rules.Breakthrough b = ctx.rules.breakthrough();
        double yearsLeft = ctx.lifespanYears(p) - ctx.ageYears(p);
        boolean desperate = yearsLeft <= b.desperateYearsLeft();
        double perYear = desperate
                ? b.desperateAttemptRatePerYear()
                : b.attemptRatePerYear() * (1.0 - b.cautionWeight() * (p.caution - 50) / 50.0)
                        * StrictMath.pow(b.retryDamping(), p.failures);
        if (!ctx.rng(p.id, Purpose.BREAKTHROUGH_ATTEMPT).chance(Rates.perDay(perYear, ctx.dpy))) {
            return;
        }
        attempt(ctx, p, ctx.realms.get(p.realm + 1), desperate);
    }

    /** Success chance before pills, for the realm {@code next}. */
    static double baseChance(SimContext ctx, Person p, RealmTable.Realm next, boolean desperate) {
        Rules.Breakthrough b = ctx.rules.breakthrough();
        double chance = next.breakthrough().baseChance();
        chance *= ctx.rootGrade(p).breakthrough();
        chance *= Cultivation.techniqueFactor(ctx, p, false);
        chance *= Math.max(0.0, 1.0 - b.ageWeight() * ctx.ageYears(p) / ctx.lifespanYears(p));
        chance *= StrictMath.pow(b.failurePenalty(), p.failures);
        chance *= Math.max(0.0, 1.0 - p.injury * b.injuryPerPoint());
        if (desperate) {
            chance *= b.desperateChanceFactor();
        }
        Person master = p.masterId >= 0 ? ctx.state.persons.get(p.masterId) : null;
        if (master != null && master.realm >= next.index()) {
            chance += b.masterBonus();
        }
        return chance;
    }

    static void attempt(SimContext ctx, Person p, RealmTable.Realm next, boolean desperate) {
        Rules.Breakthrough b = ctx.rules.breakthrough();
        RealmTable.Breakthrough bt = next.breakthrough();
        Sect sect = p.sectId >= 0 ? ctx.sect(p.sectId) : null;
        Rules.Pill pill = ctx.rules.sects().pills().get(next.id());
        boolean sectPill = sect != null && pill != null && sect.resources >= pill.cost();
        if (sectPill) {
            sect.resources -= pill.cost();
        }
        double base = Rates.clamp(baseChance(ctx, p, next, desperate), b.minChance(), b.maxChance());
        double chance = sectPill ? Rates.clamp(base + pill.bonus(), b.minChance(), b.maxChance()) : base;
        double roll = ctx.rng(p.id, Purpose.BREAKTHROUGH_ROLL).nextDouble();
        if (roll < chance) {
            String variant = sectPill && roll >= base ? TextKeys.BT_SECT_PILL
                    : desperate ? TextKeys.BT_DESPERATE : TextKeys.BT_PLAIN;
            succeed(ctx, p, next, variant, sect);
        } else {
            fail(ctx, p, next, desperate);
        }
    }

    private static void succeed(SimContext ctx, Person p, RealmTable.Realm next, String variant, Sect sect) {
        p.realm = next.index();
        p.stage = 0;
        p.progress = 0.0;
        p.failures = 0;
        if (next.titleSuffix() != null && p.daoName.isEmpty()) {
            p.daoName = Naming.daoName(ctx, ctx.rng(p.id, Purpose.DAO_NAME));
        }
        List<String> params = new ArrayList<>();
        params.add(p.name());
        if (next.titleSuffix() != null) {
            params.add(ctx.title(p));
        }
        if (variant.equals(TextKeys.BT_SECT_PILL)) {
            params.add(sect.name);
        }
        ctx.chronicle.event("breakthrough", next.breakthrough().successImportance())
                .actors(p.id).sects(Cultivation.sectIds(p)).region(p.regionId)
                .text(TextKeys.breakthrough(next.id(), variant), params.toArray(new String[0]));
    }

    private static void fail(SimContext ctx, Person p, RealmTable.Realm next, boolean desperate) {
        Rules.Breakthrough b = ctx.rules.breakthrough();
        RealmTable.Breakthrough bt = next.breakthrough();
        RealmTable.Realm current = ctx.realm(p);
        boolean woundContributed = p.injury >= b.woundCauseThreshold() && p.injuryEventId >= 0;
        long woundEvent = p.injuryEventId;
        double deathChance = bt.deathChance() * (desperate ? b.desperateDeathFactor() : 1.0);
        SimRng rng = ctx.rng(p.id, Purpose.BREAKTHROUGH_DEATH);
        if (rng.chance(deathChance)) {
            String variant = woundContributed ? "wound" : desperate ? "desperate" : "";
            int importance = Math.max(Deaths.importance(ctx, p), bt.failureImportance());
            long id = ctx.chronicle.event("death", importance).actors(p.id).sects(Cultivation.sectIds(p))
                    .region(p.regionId).cause(woundContributed ? woundEvent : -1)
                    .text(TextKeys.breakthroughDeath(next.id(), variant), p.name());
            Deaths.bury(ctx, p, Deaths.QI_DEVIATION, -1, id);
            return;
        }
        p.progress = Math.max(0.0, p.progress - bt.progressLoss() * current.stage(current.lastStage()).cap());
        p.injury = Math.min(100, p.injury + bt.injury());
        p.failures++;
        long id = ctx.chronicle.event("breakthrough_fail", bt.failureImportance()).actors(p.id)
                .sects(Cultivation.sectIds(p)).region(p.regionId)
                .text(TextKeys.breakthroughFail(next.id()), p.name());
        if (bt.injury() > 0) {
            p.injuryEventId = id;
        }
    }
}
