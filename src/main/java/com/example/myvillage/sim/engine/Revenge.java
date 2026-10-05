package com.example.myvillage.sim.engine;

import com.example.myvillage.sim.SimRng;
import com.example.myvillage.sim.data.Rules;
import com.example.myvillage.sim.model.Person;
import com.example.myvillage.sim.model.Relation;
import java.util.List;

/**
 * Revenge chains (design §4.6). A kill leaves the victim's master, disciples and close friends with
 * an enmity whose cause is the kill. An avenger acts when they judge they can win; the cautious
 * cultivate first, the reckless go at once and may die trying. Once resolved to act, the avenger
 * sets out for the killer's region and fights on arrival. The revenge kill names the wrong it
 * answers and points at the original kill, and can itself be avenged. A grudge older than the
 * rules' limit is let go.
 */
public final class Revenge {
    private Revenge() {
    }

    public static boolean isVendetta(Relation r) {
        return r.kind.equals(Relation.ENEMY) && r.causeEventId >= 0
                && (r.reason.equals("master") || r.reason.equals("disciple") || r.reason.equals("friend")
                    || r.reason.equals("sect"));
    }

    /** Reckless: aggressive enough to strike regardless of the odds. */
    static boolean reckless(SimContext ctx, Person p) {
        return p.aggression >= ctx.rules.revenge().recklessAggression();
    }

    /** Whether {@code p} judges a fight with {@code target} worth it now. */
    static boolean dares(SimContext ctx, Person p, Person target) {
        Rules.Revenge r = ctx.rules.revenge();
        if (p.aggression >= r.recklessAggression()) {
            return true;
        }
        double needed = r.confidence() + r.cautionWeight() * (p.caution - 50) / 50.0;
        return Combat.winChance(ctx, p, target) >= needed;
    }

    public static void daily(SimContext ctx, Person p) {
        Person quarry = p.quarryId >= 0 ? ctx.state.persons.get(p.quarryId) : null;
        if (p.quarryId >= 0 && quarry == null) {
            p.quarryId = -1;
        }
        if (quarry != null) {
            Relation grudge = p.relation(quarry.id, Relation.ENEMY);
            if (grudge == null) {
                p.quarryId = -1;
                return;
            }
            if (quarry.sectId >= 0 && quarry.sectId == p.sectId && (quarry.realm != p.realm
                    || quarry.rank.equals("sect_master") || p.rank.equals("sect_master"))) {
                p.quarryId = -1;
                return;
            }
            if (quarry.regionId.equals(p.regionId)) {
                p.quarryId = -1;
                Combat.fight(ctx, p, quarry, Combat.Kind.REVENGE, grudge.causeEventId, grudge.reason);
            }
            return;
        }
        if (!ctx.newYear()) {
            return;
        }
        Rules.Revenge r = ctx.rules.revenge();
        List<Relation> grudges = new java.util.ArrayList<>(p.relations);
        for (Relation g : grudges) {
            if (!isVendetta(g)) {
                continue;
            }
            Person target = ctx.state.persons.get(g.other);
            if (target == null || (target.sectId >= 0 && target.sectId == p.sectId
                    && (target.realm != p.realm || target.rank.equals("sect_master") || p.rank.equals("sect_master")))) {
                continue;
            }
            if (ctx.day() - eventDay(ctx, g.causeEventId) > (long) r.giveUpYears() * ctx.dpy) {
                p.forget(g.other, Relation.ENEMY);
                continue;
            }
            SimRng rng = ctx.rng(p.id, Purpose.REVENGE_SEEK, g.other);
            if (rng.chance(r.seekRatePerYear()) && dares(ctx, p, target)) {
                p.quarryId = target.id;
                Travel.depart(ctx, p, target.regionId, ctx.rules.travel().years()[1]);
                return;
            }
        }
    }

    /** Day of a kept event, or the current day when it was pruned. */
    private static long eventDay(SimContext ctx, long eventId) {
        var e = ctx.findEvent(eventId);
        return e == null ? ctx.day() : e.day();
    }
}
