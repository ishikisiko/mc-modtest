package com.example.myvillage.sim.engine;

import com.example.myvillage.region.runtime.GenRegion;
import com.example.myvillage.sim.SimRng;
import com.example.myvillage.sim.data.Rules;
import com.example.myvillage.sim.model.Person;
import java.util.ArrayList;
import java.util.List;

/**
 * Journeys and seclusion (design §4.3). Disciples and rogues set out for some years by wanderlust,
 * moving along 连 edges (a walled region only through its pass) toward a destination chosen by qi,
 * tier and, for the bold, danger; then they head home. Members near a bottleneck may go into
 * closed-door seclusion. A person hunting someone heads for the quarry's region instead.
 * For a rogue, "at_sect" means settled at their haunt.
 */
public final class Travel {
    private Travel() {
    }

    public static void daily(SimContext ctx, Person p) {
        switch (p.status) {
            case "secluded" -> {
                if (ctx.day() >= p.statusUntil) {
                    p.status = "at_sect";
                    p.statusUntil = -1;
                }
            }
            case "travelling" -> journey(ctx, p);
            default -> maybeLeave(ctx, p);
        }
    }

    private static void maybeLeave(SimContext ctx, Person p) {
        Rules.Travel t = ctx.rules.travel();
        SimRng rng = ctx.rng(p.id, Purpose.TRAVEL);
        double rate = t.startRatePerYear() * t.rankFactor().getOrDefault(p.rank, 1.0)
                * (0.3 + 1.4 * p.wanderlust / 100.0);
        if (rng.chance(Rates.perDay(Math.min(1.0, rate), ctx.dpy))) {
            String dest = chooseDestination(ctx, p, rng);
            if (dest == null) {
                return;
            }
            depart(ctx, p, dest, rng.range(t.years()[0], t.years()[1]));
            boolean walled = ctx.region(dest).role().equals("walled");
            p.journeys++;
            // The first journey of a life, and any into a forbidden land, are worth a line; the rest are routine.
            if (walled || p.journeys == 1) {
                ctx.chronicle.event("travel", 1).actors(p.id).sects(Cultivation.sectIds(p)).region(p.regionId)
                        .say(walled ? TextKeys.TRAVEL_WALLED : TextKeys.TRAVEL_DEPART, Anchor.of(ctx).who(p).region(dest));
            }
            return;
        }
        if (p.sectId < 0 && !p.rank.equals("rogue")) {
            return;
        }
        Rules.Seclusion s = ctx.rules.seclusion();
        double seclude = s.ratePerYear() * (Cultivation.atRealmCap(ctx, p) ? s.nearCapFactor() : 1.0)
                * (0.5 + p.ambition / 100.0);
        if (rng.chance(Rates.perDay(Math.min(1.0, seclude), ctx.dpy))) {
            p.status = "secluded";
            p.statusUntil = ctx.day() + (long) rng.range(s.years()[0], s.years()[1]) * ctx.dpy;
            ctx.chronicle.event("seclusion", 1).actors(p.id).sects(Cultivation.sectIds(p)).region(p.regionId)
                    .say(TextKeys.SECLUSION, Anchor.of(ctx).who(p));
        }
    }

    /** Starts (or extends) a journey toward {@code dest} lasting at least {@code years}. */
    static void depart(SimContext ctx, Person p, String dest, int years) {
        p.status = "travelling";
        p.destRegionId = dest;
        p.statusUntil = Math.max(p.statusUntil, ctx.day() + (long) years * ctx.dpy);
    }

    private static void journey(SimContext ctx, Person p) {
        Rules.Travel t = ctx.rules.travel();
        Person quarry = p.quarryId >= 0 ? ctx.state.persons.get(p.quarryId) : null;
        if (quarry != null) {
            p.destRegionId = quarry.regionId;
        } else if (ctx.day() >= p.statusUntil) {
            p.destRegionId = p.homeRegionId;
            if (p.regionId.equals(p.homeRegionId)) {
                p.status = "at_sect";
                p.statusUntil = -1;
                p.destRegionId = "";
                return;
            }
        }
        SimRng rng = ctx.rng(p.id, Purpose.TRAVEL_MOVE);
        if (!rng.chance(Rates.poissonPerDay(t.movesPerYear(), ctx.dpy))) {
            return;
        }
        if (p.destRegionId.isEmpty() || p.regionId.equals(p.destRegionId)) {
            if (quarry != null) {
                return;
            }
            String next = chooseDestination(ctx, p, rng);
            p.destRegionId = next == null ? p.homeRegionId : next;
        }
        p.regionId = ctx.nextStep(p.regionId, p.destRegionId);
    }

    /** A destination other than the current region, weighted by qi, tier and (for the bold) danger. */
    static String chooseDestination(SimContext ctx, Person p, SimRng rng) {
        Rules.Travel t = ctx.rules.travel();
        int boldness = 100 - p.caution;
        List<GenRegion> options = new ArrayList<>();
        for (GenRegion r : ctx.graph.regions()) {
            if (r.id().equals(p.regionId) || ctx.nextStep(p.regionId, r.id()).equals(p.regionId)) {
                continue;
            }
            if (r.role().equals("walled") && boldness < t.walledBoldness()) {
                continue;
            }
            options.add(r);
        }
        if (options.isEmpty()) {
            return null;
        }
        double[] w = new double[options.size()];
        for (int i = 0; i < w.length; i++) {
            GenRegion r = options.get(i);
            w[i] = t.qiWeight() * SimContext.qiMid(r) + t.tierWeight() * r.tier()
                    + t.dangerWeight() * SimContext.dangerMid(r) * (boldness - 50) / 50.0;
            w[i] = Math.max(0.1, w[i]);
        }
        return options.get(rng.weighted(w)).id();
    }
}
