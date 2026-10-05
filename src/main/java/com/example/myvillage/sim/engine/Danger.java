package com.example.myvillage.sim.engine;

import com.example.myvillage.region.runtime.GenRegion;
import com.example.myvillage.sim.SimRng;
import com.example.myvillage.sim.data.ContentTables;
import com.example.myvillage.sim.data.Rules;
import com.example.myvillage.sim.model.Person;
import java.util.ArrayList;
import java.util.List;

/**
 * Beasts on the road (design §4.3): travellers meet beasts at a rate that climbs steeply with the
 * region's danger; the beast's rank follows the danger. Winning brings a little progress; losing
 * means flight, a wound, or death in the beast's jaws.
 */
public final class Danger {
    private Danger() {
    }

    public static void daily(SimContext ctx, Person p) {
        if (!p.status.equals("travelling")) {
            return;
        }
        Rules.Danger d = ctx.rules.danger();
        GenRegion region = ctx.region(p.regionId);
        double danger = SimContext.dangerMid(region) / 10.0;
        double perYear = Math.min(1.0, d.beastRatePerYear() * StrictMath.pow(danger, d.dangerExponent()));
        SimRng rng = ctx.rng(p.id, Purpose.DANGER);
        if (!rng.chance(Rates.perDay(perYear, ctx.dpy))) {
            return;
        }
        ContentTables.Beast beast = pickBeast(ctx, rng, SimContext.dangerMid(region));
        double k = ctx.rules.combat().steepness();
        double noise = ctx.rules.combat().noise();
        double mine = StrictMath.pow(Combat.power(ctx, p) * rng.uniform(1 - noise, 1 + noise), k);
        double theirs = StrictMath.pow(d.beastPower()[beast.rank() - 1] * rng.uniform(1 - noise, 1 + noise), k);
        if (rng.nextDouble() < mine / (mine + theirs)) {
            p.progress += d.slayProgressYears() * Cultivation.yearlyRate(ctx, p);
            if (beast.rank() < d.recordedBeastRank()) {
                return;
            }
            ctx.chronicle.event("beast", 1).actors(p.id).sects(Cultivation.sectIds(p)).region(p.regionId)
                    .say(TextKeys.BEAST_SLAY, Anchor.of(ctx).who(p).region(p.regionId).add(beast.name()));
            return;
        }
        if (!rng.chance(d.fleeChance()) && rng.chance(d.deathOnLoss())) {
            long id = ctx.chronicle.event("death", Deaths.importance(ctx, p)).actors(p.id)
                    .sects(Cultivation.sectIds(p)).region(p.regionId)
                    .say(TextKeys.DEATH_BEAST, Anchor.of(ctx).who(p).age(p).region(p.regionId).add(beast.name()));
            Deaths.bury(ctx, p, Deaths.BEAST, -1, id);
            return;
        }
        long id = ctx.chronicle.event("beast", 1).actors(p.id).sects(Cultivation.sectIds(p)).region(p.regionId)
                .say(TextKeys.BEAST_WOUND, Anchor.of(ctx).who(p).region(p.regionId).add(beast.name()));
        p.injury = Math.min(100, p.injury + d.injury());
        p.injuryEventId = id;
        p.injurerId = -1;
    }

    /** A beast whose rank (1..4) is drawn near danger / 2.5, from the lore table. */
    static ContentTables.Beast pickBeast(SimContext ctx, SimRng rng, double danger) {
        double[] w = new double[4];
        for (int r = 1; r <= 4; r++) {
            w[r - 1] = StrictMath.exp(-Math.abs(r - danger / 2.5));
        }
        int rank = rng.weighted(w) + 1;
        List<ContentTables.Beast> pool = new ArrayList<>();
        for (int dist = 0; pool.isEmpty() && dist < 4; dist++) {
            for (ContentTables.Beast b : ctx.data.lore().beasts()) {
                if (Math.abs(b.rank() - rank) == dist) {
                    pool.add(b);
                }
            }
        }
        return pool.get(rng.nextInt(pool.size()));
    }
}
