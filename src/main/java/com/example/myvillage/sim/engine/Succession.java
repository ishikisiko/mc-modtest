package com.example.myvillage.sim.engine;

import com.example.myvillage.sim.SimEvent;
import com.example.myvillage.sim.SimRng;
import com.example.myvillage.sim.data.Rules;
import com.example.myvillage.sim.model.Person;
import com.example.myvillage.sim.model.Sect;
import com.example.myvillage.sim.model.Tombstone;
import java.util.ArrayList;
import java.util.List;

/**
 * Fills an emptied master's seat at the end of the day (design §4.7). The strongest elder succeeds
 * (cause: the death). Two comparable elders, the second ambitious, may contest the seat in a duel;
 * the loser may leave with followers and found a rival sect (cause: the succession). With no elder
 * the strongest member takes over; if they are below the rules' minimum the sect begins to decline.
 * A sect with no one left is extinct.
 */
public final class Succession {
    private Succession() {
    }

    public static void settle(SimContext ctx) {
        for (Sect sect : new ArrayList<>(ctx.state.sects.values())) {
            if (sect.active() && sect.masterId < 0) {
                fill(ctx, sect);
            }
        }
    }

    private static void fill(SimContext ctx, Sect sect) {
        Rules.Succession r = ctx.rules.succession();
        long cause = sect.vacancyCauseEventId;
        int predecessorId = predecessor(ctx, cause);
        List<Person> elders = new ArrayList<>();
        for (Person p : ctx.members(sect.id)) {
            if (p.rank.equals("elder")) {
                elders.add(p);
            }
        }
        elders.sort((x, y) -> Double.compare(SimContext.standing(y), SimContext.standing(x)));
        // Among elders comparable to the strongest, the one with the most years left is preferred.
        for (int i = 1; i < elders.size(); i++) {
            Person e = elders.get(i);
            if (SimContext.standing(elders.get(0)) - SimContext.standing(e) <= r.contestGap()
                    && yearsLeft(ctx, e) > yearsLeft(ctx, elders.get(0))) {
                elders.remove(i);
                elders.add(0, e);
            }
        }
        Person heir = elders.isEmpty() ? strongestWithTime(ctx, ctx.members(sect.id), r) : elders.get(0);
        if (heir == null) {
            long id = ctx.chronicle.event("sect_extinct", 3).sects(sect.id).region(sect.homeRegionId).cause(cause)
                    .say(TextKeys.SECT_EXTINCT, sect.name, ctx.nameOf(predecessorId));
            SectPolitics.dissolve(ctx, sect, true, id);
            return;
        }
        Person rival = null;
        if (elders.size() >= 2) {
            Person second = elders.get(1);
            SimRng rng = ctx.rng(sect.id, Purpose.SECT_SUCCESSION);
            if (SimContext.standing(heir) - SimContext.standing(second) <= r.contestGap()
                    && second.ambition >= r.contestAmbition() && rng.chance(r.contestChance())) {
                rival = second;
            }
        }
        if (rival != null) {
            Combat.Result duel = Combat.fight(ctx, rival, heir, Combat.Kind.SUCCESSION, cause, "");
            Person winner = duel.winner();
            Person loser = duel.loser();
            long id = install(ctx, sect, winner, cause, TextKeys.SUCCESSION_CONTESTED,
                    Anchor.of(ctx).who(winner).add(ctx.nameOf(predecessorId), loser.name()));
            if (ctx.alive(loser) && ctx.rng(loser.id, Purpose.SECT_SPLIT).chance(
                    r.leaveChance() * (1.5 - loser.loyalty / 100.0))) {
                SectPolitics.foundSect(ctx, loser, sect, SectPolitics.followersOf(ctx, loser, sect), id,
                        TextKeys.SECT_SPLIT);
            }
            return;
        }
        String family = heir.rank.equals("elder") ? TextKeys.SUCCESSION : TextKeys.SUCCESSION_JUNIOR;
        install(ctx, sect, heir, cause, family, Anchor.of(ctx).who(heir).add(ctx.nameOf(predecessorId)));
        boolean unfit = !SectAffairs.reached(ctx, heir, r.minMaster()) || yearsLeft(ctx, heir) < r.minYearsLeft();
        if (unfit && sect.declineSinceDay < 0) {
            SectPolitics.startDecline(ctx, sect, heir, cause);
        }
    }

    private static long install(SimContext ctx, Sect sect, Person heir, long cause, String family, Anchor params) {
        String[] built = params.build();
        heir.rank = "sect_master";
        if (!sect.heritageId.isEmpty()) {
            // A heritage sect's master practises the chain's senior technique (People.sectTechnique).
            heir.techniqueId = SectAffairs.upgradeTechnique(ctx, heir, People.sectTechnique(ctx, sect, heir.rank));
        }
        sect.masterId = heir.id;
        sect.masterSinceDay = ctx.day();
        sect.vacancyCauseEventId = -1;
        return ctx.chronicle.event("succession", 3).actors(heir.id).sects(sect.id).region(sect.homeRegionId)
                .cause(cause).say(family, built);
    }

    /** The strongest member, preferring among comparable ones the one with the most years left. */
    private static Person strongestWithTime(SimContext ctx, List<Person> members, Rules.Succession r) {
        Person top = strongest(members, false);
        Person best = top;
        for (Person p : members) {
            if (top != null && SimContext.standing(top) - SimContext.standing(p) <= r.contestGap()
                    && yearsLeft(ctx, p) > yearsLeft(ctx, best)) {
                best = p;
            }
        }
        return best;
    }

    static double yearsLeft(SimContext ctx, Person p) {
        return ctx.lifespanYears(p) - ctx.ageYears(p);
    }

    static Person strongest(List<Person> members, boolean eldersOnly) {
        Person best = null;
        for (Person p : members) {
            if (eldersOnly && !p.rank.equals("elder")) {
                continue;
            }
            if (best == null || SimContext.standing(p) > SimContext.standing(best)) {
                best = p;
            }
        }
        return best;
    }

    /** The person whose death emptied the seat, or -1. */
    private static int predecessor(SimContext ctx, long deathEventId) {
        SimEvent e = ctx.findEvent(deathEventId);
        if (e == null) {
            return -1;
        }
        Tombstone t = ctx.state.tombstones.get(e.subject());
        return t == null ? -1 : t.id;
    }
}
