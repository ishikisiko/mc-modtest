package com.example.myvillage.sim.engine;

import com.example.myvillage.region.runtime.GenRegion;
import com.example.myvillage.sim.SimRng;
import com.example.myvillage.sim.data.ContentTables;
import com.example.myvillage.sim.data.EncounterTable;
import com.example.myvillage.sim.data.Rules;
import com.example.myvillage.sim.model.Boon;
import com.example.myvillage.sim.model.Person;
import com.example.myvillage.sim.model.RegionState;
import java.util.ArrayList;
import java.util.List;

/**
 * Fortunes (奇遇, design §4.4). Travellers (and, more rarely, the secluded) stumble on entries of
 * {@code encounters.json}; high-tier, dangerous regions favour the rare ones, and each find
 * depletes the region's unclaimed richness for a while. Effects can change a life: a technique of a
 * higher grade, pills, lifespan, a purer root, an artifact; some are traps. A contested find makes
 * another traveller fight for it. Every boon remembers the event that granted it, so a later
 * breakthrough or founding can name it.
 */
public final class Fortunes {
    private Fortunes() {
    }

    /** Once a year: depleted regions recover. */
    public static void yearly(SimContext ctx) {
        int regen = ctx.rules.fortune().richnessRegenPerYear();
        for (RegionState r : ctx.state.regions.values()) {
            r.richness = Math.min(100, r.richness + regen);
        }
    }

    public static void daily(SimContext ctx, Person p) {
        Rules.Fortune f = ctx.rules.fortune();
        GenRegion region = ctx.region(p.regionId);
        RegionState rs = ctx.state.regions.get(p.regionId);
        double richness = rs == null ? 1.0 : rs.richness / 100.0;
        double perYear;
        if (p.status.equals("travelling")) {
            perYear = f.ratePerYear() * (1.0 + f.tierWeight() * region.tier() / 20.0) * richness;
        } else if (p.status.equals("secluded")) {
            perYear = f.secludedRatePerYear();
        } else {
            return;
        }
        SimRng rng = ctx.rng(p.id, Purpose.FORTUNE);
        if (!rng.chance(Rates.perDay(Math.min(1.0, perYear), ctx.dpy))) {
            return;
        }
        EncounterTable.Encounter e = pick(ctx, p, region, ctx.rng(p.id, Purpose.FORTUNE_PICK));
        if (e != null) {
            befall(ctx, p, e, region, rs);
        }
    }

    static EncounterTable.Encounter pick(SimContext ctx, Person p, GenRegion region, SimRng rng) {
        Rules.Fortune f = ctx.rules.fortune();
        double danger = SimContext.dangerMid(region);
        double quality = 0.5 * region.tier() / 20.0 + 0.5 * danger / 10.0;
        String realm = ctx.realm(p).id();
        List<EncounterTable.Encounter> options = new ArrayList<>();
        List<Double> weights = new ArrayList<>();
        for (EncounterTable.Encounter e : ctx.data.encounters().encounters()) {
            if (danger < e.minDanger() || danger > e.maxDanger() || region.tier() < e.minTier()) {
                continue;
            }
            if (!e.realms().isEmpty() && !e.realms().contains(realm)) {
                continue;
            }
            if (!e.statuses().isEmpty() && !e.statuses().contains(p.status)) {
                continue;
            }
            options.add(e);
            weights.add(e.weight() * StrictMath.pow(f.rarityBoost() * quality, e.rarity()));
        }
        if (options.isEmpty()) {
            return null;
        }
        double[] w = new double[weights.size()];
        for (int i = 0; i < w.length; i++) {
            w[i] = weights.get(i);
        }
        return options.get(rng.weighted(w));
    }

    private static void befall(SimContext ctx, Person p, EncounterTable.Encounter e, GenRegion region, RegionState rs) {
        SimRng rng = ctx.rng(p.id, Purpose.FORTUNE_EFFECT);
        if (e.quiet()) {
            for (EncounterTable.Effect fx : e.effects()) {
                if (fx.kind().equals("progress")) {
                    p.progress += fx.amount() * Cultivation.yearlyRate(ctx, p);
                }
            }
            p.insightDay = ctx.day();
            return;
        }
        ContentTables.Site site = e.siteKind() == null ? null : pickSite(ctx, rng, e.siteKind());
        ContentTables.Technique technique = null;
        ContentTables.Artifact artifact = null;
        for (EncounterTable.Effect fx : e.effects()) {
            if (fx.kind().equals("technique")) {
                technique = pickTechnique(ctx, p, rng, fx.grade());
            } else if (fx.kind().equals("artifact")) {
                artifact = pickArtifact(ctx, rng, fx.grade());
            }
        }
        Anchor params = Anchor.of(ctx).who(p).region(p.regionId);
        if (site != null) {
            params.add(site.name());
        }
        if (technique != null) {
            params.add(technique.name());
        } else if (artifact != null) {
            params.add(artifact.name());
        }
        long id = ctx.chronicle.event("fortune", e.importance()).actors(p.id).sects(Cultivation.sectIds(p))
                .region(p.regionId).say(TextKeys.fortune(e), params);
        p.fortunes++;
        if (rs != null) {
            rs.richness = Math.max(0, rs.richness - ctx.rules.fortune().richnessCost());
        }
        String place = site != null ? site.name() : region.displayName();
        Person owner = p;
        if (e.contested()) {
            owner = contest(ctx, p, e, id, technique != null ? technique.name() : artifact != null ? artifact.name() : place);
            if (owner == null) {
                return;
            }
        }
        for (EncounterTable.Effect fx : e.effects()) {
            if (!ctx.alive(owner)) {
                return;
            }
            apply(ctx, owner, fx, id, technique, artifact, place, rng);
        }
    }

    /** Another traveller may contest the find; returns who keeps it (null when the finder died for nothing). */
    private static Person contest(SimContext ctx, Person finder, EncounterTable.Encounter e, long eventId, String item) {
        SimRng rng = ctx.rng(finder.id, Purpose.FORTUNE_EFFECT, 1);
        if (!rng.chance(ctx.rules.fortune().contestChance())) {
            return finder;
        }
        List<Person> rivals = new ArrayList<>();
        for (Person q : ctx.peopleIn(finder.regionId)) {
            if (q != finder && ctx.alive(q) && q.status.equals("travelling")
                    && (q.sectId < 0 || q.sectId != finder.sectId)) {
                rivals.add(q);
            }
        }
        if (rivals.isEmpty()) {
            return finder;
        }
        Person rival = rivals.get(rng.nextInt(rivals.size()));
        Combat.Result r = Combat.fight(ctx, rival, finder, Combat.Kind.CONTEST, eventId, item);
        return ctx.alive(r.winner()) ? r.winner() : null;
    }

    private static void apply(SimContext ctx, Person p, EncounterTable.Effect fx, long eventId,
                              ContentTables.Technique technique, ContentTables.Artifact artifact, String place, SimRng rng) {
        switch (fx.kind()) {
            case "progress" -> p.progress += fx.amount() * Cultivation.yearlyRate(ctx, p);
            case "technique" -> {
                ContentTables.Technique current = ctx.technique(p);
                if (technique != null && (current == null || technique.gradeRank() > current.gradeRank())) {
                    p.techniqueId = technique.id();
                    p.techniqueEventId = eventId;
                }
            }
            case "breakthrough_pill" -> p.boons.add(new Boon(Boon.BREAKTHROUGH_PILL, place, fx.amount(), eventId));
            case "lifespan" -> {
                p.bonusLifespanYears += (int) Math.round(fx.amount());
                p.lifespanEventId = eventId;
            }
            case "root" -> purify(ctx, p, (int) Math.round(fx.amount()), eventId);
            case "artifact" -> {
                if (artifact != null) {
                    p.boons.add(new Boon(Boon.ARTIFACT, artifact.id(),
                            ctx.rules.artifactPower().get(artifact.grade()), eventId));
                }
            }
            case "injury" -> {
                p.injury = Math.min(100, p.injury + (int) Math.round(fx.amount()));
                p.injuryEventId = eventId;
                p.injurerId = -1;
            }
            case "death" -> {
                if (rng.chance(fx.amount())) {
                    long id = ctx.chronicle.event("death", Deaths.importance(ctx, p)).actors(p.id)
                            .sects(Cultivation.sectIds(p)).region(p.regionId).cause(eventId)
                            .say(TextKeys.DEATH_TRAP, Anchor.of(ctx).who(p).age(p).region(p.regionId));
                    Deaths.bury(ctx, p, Deaths.TRAP, -1, id);
                }
            }
            default -> throw new IllegalStateException("unknown effect " + fx.kind());
        }
    }

    /** Moves basis points from the weakest strong element into the strongest one. */
    static void purify(SimContext ctx, Person p, int bp, long eventId) {
        int threshold = ctx.rules.roots().elementThresholdBp();
        int strongest = 0;
        int weakest = -1;
        for (int e = 0; e < 5; e++) {
            if (p.root[e] > p.root[strongest]) {
                strongest = e;
            }
        }
        for (int e = 0; e < 5; e++) {
            if (e != strongest && p.root[e] >= threshold && (weakest < 0 || p.root[e] < p.root[weakest])) {
                weakest = e;
            }
        }
        if (weakest < 0) {
            return;
        }
        int moved = Math.min(bp, p.root[weakest]);
        p.root[weakest] -= moved;
        p.root[strongest] += moved;
        p.rootEventId = eventId;
    }

    static ContentTables.Site pickSite(SimContext ctx, SimRng rng, String kind) {
        List<ContentTables.Site> pool = new ArrayList<>();
        for (ContentTables.Site s : ctx.data.lore().sites()) {
            if (s.kind().equals(kind)) {
                pool.add(s);
            }
        }
        return pool.get(rng.nextInt(pool.size()));
    }

    /** A technique of the grade, preferring one whose element suits the person's root. */
    static ContentTables.Technique pickTechnique(SimContext ctx, Person p, SimRng rng, String grade) {
        List<ContentTables.Technique> all = People.techniquesOfGrade(ctx, grade);
        List<ContentTables.Technique> suited = new ArrayList<>();
        for (ContentTables.Technique t : all) {
            if (Roots.strongIn(ctx.rules.roots(), p.root, t.element())) {
                suited.add(t);
            }
        }
        List<ContentTables.Technique> pool = suited.isEmpty() ? all : suited;
        return pool.get(rng.nextInt(pool.size()));
    }

    static ContentTables.Artifact pickArtifact(SimContext ctx, SimRng rng, String grade) {
        List<ContentTables.Artifact> pool = new ArrayList<>();
        for (ContentTables.Artifact a : ctx.data.lore().artifacts()) {
            if (a.grade().equals(grade)) {
                pool.add(a);
            }
        }
        if (pool.isEmpty()) {
            pool.addAll(ctx.data.lore().artifacts());
        }
        return pool.get(rng.nextInt(pool.size()));
    }
}
