package com.example.myvillage.sim.engine;

import com.example.myvillage.region.runtime.GenRegion;
import com.example.myvillage.sim.SimRng;
import com.example.myvillage.sim.data.ContentTables;
import com.example.myvillage.sim.data.Rules;
import com.example.myvillage.sim.model.Person;
import com.example.myvillage.sim.model.Sect;
import java.util.ArrayList;
import java.util.List;

/**
 * New blood, once a year (design §4.9 and the recruitment half of §4.7). The number of entrants
 * pulls the population toward the tier's target. Each one joins a sect (chosen by prestige, damped
 * for oversized sects; prestigious sects test more candidates and keep the most gifted) or starts
 * out as a rogue. No births or childhoods: an entrant is 炼气一层, aged about 12–18.
 */
public final class Entrants {
    private Entrants() {
    }

    public static void yearly(SimContext ctx) {
        Rules.Tier tier = ctx.rules.tier(ctx.state.tierId);
        Rules.Entrants e = ctx.rules.entrants();
        int population = ctx.state.persons.size();
        double expected = e.baseRate() * tier.population() + e.gain() * (tier.population() - population);
        expected = Rates.clamp(expected, 0.0, e.maxFractionPerYear() * tier.population());
        int count = ctx.rng(0, Purpose.SECT_RECRUIT_COUNT).roundStochastic(expected);
        for (int i = 0; i < count; i++) {
            admit(ctx, ctx.rng(i, Purpose.ENTRANT), tier);
        }
    }

    private static void admit(SimContext ctx, SimRng rng, Rules.Tier tier) {
        List<Sect> sects = ctx.activeSects();
        Sect sect = null;
        if (!sects.isEmpty() && !rng.chance(tier.rogueShare())) {
            sect = pickSect(ctx, rng, sects, tier);
        }
        Rules.Entrants e = ctx.rules.entrants();
        long ageDays = (long) rng.range(e.age()[0], e.age()[1]) * ctx.dpy + rng.nextInt(ctx.dpy);
        if (sect == null) {
            int[] root = Roots.draw(ctx.rules.roots(), rng);
            Person p = People.create(ctx, rng, root, 0, 0, ageDays, rogueHaunt(ctx, rng));
            if (rng.chance(e.rogueTechniqueChance())) {
                List<ContentTables.Technique> huang = People.techniquesOfGrade(ctx, ContentTables.GRADE_ORDER.get(0));
                p.techniqueId = huang.get(rng.nextInt(huang.size())).id();
            }
            ctx.chronicle.event("entrant", 1).actors(p.id).region(p.regionId)
                    .text(TextKeys.ENTRANT_ROGUE, p.name(), ctx.regionName(p.regionId));
            return;
        }
        Rules.Sects r = ctx.rules.sects();
        int candidates = Math.min(r.maxCandidates(), 1 + (int) (sect.prestige / r.prestigePerCandidate()));
        int[] root = Roots.drawBest(ctx.rules.roots(), rng, candidates);
        Person p = People.create(ctx, rng, root, 0, 0, ageDays, sect.homeRegionId);
        People.join(ctx, p, sect, "outer");
        p.techniqueId = sect.basicTechniqueId;
        int grade = Roots.gradeIndex(ctx.rules.roots(), root);
        if (grade == 0) {
            ctx.chronicle.event("recruit", 2).actors(p.id).sects(sect.id).region(sect.homeRegionId)
                    .text(TextKeys.RECRUIT_PRODIGY, p.name(), sect.name,
                            TextKeys.rootGrade(ctx.rules.roots().grades().get(0).id()));
        } else {
            ctx.chronicle.event("recruit", 1).actors(p.id).sects(sect.id).region(sect.homeRegionId)
                    .text(TextKeys.RECRUIT, p.name(), sect.name);
        }
    }

    private static Sect pickSect(SimContext ctx, SimRng rng, List<Sect> sects, Rules.Tier tier) {
        Rules.Sects r = ctx.rules.sects();
        double nominal = Math.max(1.0, tier.population() * (1.0 - tier.rogueShare()) / tier.sects());
        double[] weights = new double[sects.size()];
        for (int i = 0; i < weights.length; i++) {
            Sect s = sects.get(i);
            int members = ctx.members(s.id).size();
            weights[i] = (s.prestige + r.recruitWeightFloor()) / (1.0 + r.recruitSizeDamping() * members / nominal);
        }
        return sects.get(rng.weighted(weights));
    }

    /** A rogue starts in a region that admits sects (the settled lands), chosen uniformly. */
    static String rogueHaunt(SimContext ctx, SimRng rng) {
        List<String> ids = new ArrayList<>();
        for (GenRegion r : ctx.graph.regions()) {
            if (SimContext.admitsSects(r)) {
                ids.add(r.id());
            }
        }
        return ids.get(rng.nextInt(ids.size()));
    }
}
