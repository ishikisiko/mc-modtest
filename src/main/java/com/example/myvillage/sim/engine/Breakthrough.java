package com.example.myvillage.sim.engine;

import com.example.myvillage.sim.SimRng;
import com.example.myvillage.sim.data.ContentTables;
import com.example.myvillage.sim.data.RealmTable;
import com.example.myvillage.sim.data.Rules;
import com.example.myvillage.sim.model.Boon;
import com.example.myvillage.sim.model.Person;
import com.example.myvillage.sim.model.Sect;

/**
 * Great-realm breakthroughs (design §4.2). At the cap of a realm's last stage a person attempts
 * now and then (less often after each failure), or desperately when lifespan is nearly out. The
 * chance comes from root, technique, age, earlier failures, old injuries, master, a pill from a
 * fortune and a sect pill. When a fortune's technique, pill or root cleansing tipped a success, the
 * line names it and points at that fortune; when an old wound contributed to a death, the line names
 * whoever dealt it and points at that fight.
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

    /** Success chance before pills, for the realm {@code next}; {@code techniqueFactor} as used. */
    static double baseChance(SimContext ctx, Person p, RealmTable.Realm next, boolean desperate,
                             double techniqueFactor, double rootFactor) {
        Rules.Breakthrough b = ctx.rules.breakthrough();
        double chance = next.breakthrough().baseChance();
        chance *= rootFactor;
        chance *= techniqueFactor;
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
        Sect sect = p.sectId >= 0 ? ctx.sect(p.sectId) : null;
        Rules.Pill pill = ctx.rules.sects().pills().get(next.id());
        boolean sectPill = sect != null && pill != null && sect.resources >= pill.cost();
        if (sectPill) {
            sect.resources -= pill.cost();
        }
        Boon fortunePill = null;
        for (Boon boon : p.boons) {
            if (boon.kind.equals(Boon.BREAKTHROUGH_PILL) && (fortunePill == null || boon.amount > fortunePill.amount)) {
                fortunePill = boon;
            }
        }
        if (fortunePill != null) {
            p.boons.remove(fortunePill);
        }
        double tech = Cultivation.techniqueFactor(ctx, p, false);
        double root = ctx.rootGrade(p).breakthrough();
        // Without the fortune's technique the person would have had their sect's (or no) technique.
        double plainTech = p.techniqueEventId >= 0 ? fallbackTechniqueFactor(ctx, p) : tech;
        double plainRoot = p.rootEventId >= 0 ? ctx.rules.roots().grades().get(
                Math.min(ctx.rules.roots().grades().size() - 1, Roots.gradeIndex(ctx.rules.roots(), p.root) + 1))
                .breakthrough() : root;
        double lo = b.minChance();
        double hi = b.maxChance();
        double base = Rates.clamp(baseChance(ctx, p, next, desperate, tech, root), lo, hi);
        double pills = (sectPill ? pill.bonus() : 0.0) + (fortunePill != null ? fortunePill.amount : 0.0);
        double chance = Rates.clamp(base + pills, lo, hi);
        double roll = ctx.rng(p.id, Purpose.BREAKTHROUGH_ROLL).nextDouble();
        if (roll >= chance) {
            fail(ctx, p, next, desperate);
            return;
        }
        // What tipped it: the first boon without which this roll would have failed.
        double noFortunePill = Rates.clamp(base + (sectPill ? pill.bonus() : 0.0), lo, hi);
        double noTech = Rates.clamp(baseChance(ctx, p, next, desperate, plainTech, root) + pills, lo, hi);
        double noRoot = Rates.clamp(baseChance(ctx, p, next, desperate, tech, plainRoot) + pills, lo, hi);
        String variant;
        String extra = null;
        long cause = -1;
        ContentTables.Technique t = ctx.technique(p);
        if (fortunePill != null && roll >= noFortunePill) {
            variant = TextKeys.BT_FORTUNE_PILL;
            extra = fortunePill.refId;
            cause = fortunePill.sourceEventId;
        } else if (p.techniqueEventId >= 0 && t != null && roll >= noTech) {
            variant = TextKeys.BT_FORTUNE_TECHNIQUE;
            extra = t.name();
            cause = p.techniqueEventId;
        } else if (p.rootEventId >= 0 && roll >= noRoot && ctx.findEvent(p.rootEventId) != null) {
            variant = TextKeys.BT_FORTUNE_ROOT;
            extra = ctx.regionName(ctx.findEvent(p.rootEventId).regionId());
            cause = p.rootEventId;
        } else if (sectPill && roll >= Rates.clamp(base + (fortunePill != null ? fortunePill.amount : 0.0), lo, hi)) {
            variant = TextKeys.BT_SECT_PILL;
            extra = sect.name;
        } else {
            variant = desperate ? TextKeys.BT_DESPERATE : TextKeys.BT_PLAIN;
        }
        succeed(ctx, p, next, variant, extra, cause);
    }

    /** The technique factor the person would have from their sect alone (or none). */
    private static double fallbackTechniqueFactor(SimContext ctx, Person p) {
        Rules.Techniques rules = ctx.rules.techniques();
        Sect sect = p.sectId >= 0 ? ctx.sect(p.sectId) : null;
        ContentTables.Technique t = sect == null ? null : ctx.data.technique(People.sectTechnique(sect, p.rank));
        if (t == null) {
            return rules.none().breakthrough();
        }
        double f = rules.grades().get(t.grade()).breakthrough();
        if (Roots.strongIn(ctx.rules.roots(), p.root, t.element())) {
            f += rules.elementMatchBonus();
        }
        return Math.min(f, Cultivation.techniqueFactor(ctx, p, false));
    }

    private static void succeed(SimContext ctx, Person p, RealmTable.Realm next, String variant, String extra, long cause) {
        Anchor params = Anchor.of(ctx).who(p).age(p);
        int importance = next.breakthrough().successImportance();
        boolean fortune = cause >= 0;
        if (next.index() == 1 && !fortune && ctx.ageYears(p) > ctx.rules.importance().prodigyFoundationAge()) {
            importance = 1;
        }
        p.realm = next.index();
        p.stage = 0;
        p.progress = 0.0;
        p.failures = 0;
        if (next.titleSuffix() != null) {
            if (p.daoName.isEmpty()) {
                p.daoName = Naming.daoName(ctx, ctx.rng(p.id, Purpose.DAO_NAME));
            }
            params.add(ctx.title(p));
        }
        if (extra != null) {
            params.add(extra);
        }
        ctx.chronicle.event("breakthrough", importance).actors(p.id).sects(Cultivation.sectIds(p))
                .region(p.regionId).cause(cause).say(TextKeys.breakthrough(next.id(), variant), params);
    }

    private static void fail(SimContext ctx, Person p, RealmTable.Realm next, boolean desperate) {
        Rules.Breakthrough b = ctx.rules.breakthrough();
        RealmTable.Breakthrough bt = next.breakthrough();
        RealmTable.Realm current = ctx.realm(p);
        boolean woundContributed = p.injury >= b.woundCauseThreshold() && p.injuryEventId >= 0;
        long woundEvent = p.injuryEventId;
        int injurer = p.injurerId;
        double deathChance = bt.deathChance() * (desperate ? b.desperateDeathFactor() : 1.0);
        SimRng rng = ctx.rng(p.id, Purpose.BREAKTHROUGH_DEATH);
        if (rng.chance(deathChance)) {
            int importance = Math.max(Deaths.importance(ctx, p), bt.failureImportance());
            Chronicle.Builder event = ctx.chronicle.event("death", importance).actors(p.id)
                    .sects(Cultivation.sectIds(p)).region(p.regionId);
            long id;
            if (woundContributed && injurer >= 0 && ctx.nameOf(injurer).length() > 0) {
                id = event.cause(woundEvent).say(TextKeys.breakthroughDeath(next.id(), "wound_by"),
                        Anchor.of(ctx).who(p).age(p).who(injurer));
            } else if (woundContributed) {
                id = event.cause(woundEvent).say(TextKeys.breakthroughDeath(next.id(), "wound"),
                        Anchor.of(ctx).who(p).age(p));
            } else {
                id = event.say(TextKeys.breakthroughDeath(next.id(), desperate ? "desperate" : ""),
                        Anchor.of(ctx).who(p).age(p));
            }
            Deaths.bury(ctx, p, Deaths.QI_DEVIATION, -1, id);
            return;
        }
        p.progress = Math.max(0.0, p.progress - bt.progressLoss() * current.stage(current.lastStage()).cap());
        p.injury = Math.min(100, p.injury + bt.injury());
        p.failures++;
        long id = ctx.chronicle.event("breakthrough_fail", bt.failureImportance()).actors(p.id)
                .sects(Cultivation.sectIds(p)).region(p.regionId)
                .say(TextKeys.breakthroughFail(next.id()), Anchor.of(ctx).who(p));
        if (bt.injury() > 0 && !woundContributed) {
            p.injuryEventId = id;
            p.injurerId = -1;
        }
    }
}
