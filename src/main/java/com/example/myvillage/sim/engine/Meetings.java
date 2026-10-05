package com.example.myvillage.sim.engine;

import com.example.myvillage.sim.SimRng;
import com.example.myvillage.sim.data.Rules;
import com.example.myvillage.sim.model.Boon;
import com.example.myvillage.sim.model.Person;
import com.example.myvillage.sim.model.Relation;
import com.example.myvillage.sim.model.SectRelation;
import java.util.List;

/**
 * Chance meetings (design §4.5): now and then a person runs into someone else in the same region
 * (one sampled partner, not all pairs). Members of sects at war fight on sight; old enemies may
 * settle accounts; strangers become friends, quarrel, spar, or the strong rob the weak.
 */
public final class Meetings {
    private Meetings() {
    }

    public static void daily(SimContext ctx, Person p) {
        Rules.Meetings m = ctx.rules.meetings();
        double perYear = m.ratePerYear().getOrDefault(p.status, 0.0);
        SimRng rng = ctx.rng(p.id, Purpose.MEETING);
        if (perYear <= 0 || !rng.chance(Rates.poissonPerDay(perYear, ctx.dpy))) {
            return;
        }
        List<Person> here = ctx.peopleIn(p.regionId);
        if (here.size() < 2) {
            return;
        }
        Person q = here.get(ctx.rng(p.id, Purpose.MEETING_PARTNER).nextInt(here.size()));
        if (q == p || !ctx.alive(q) || (p.status.equals("at_sect") && q.status.equals("at_sect")
                && p.sectId >= 0 && p.sectId == q.sectId)) {
            return;
        }
        meet(ctx, p, q, ctx.rng(p.id, Purpose.MEETING_OUTCOME));
    }

    static void meet(SimContext ctx, Person p, Person q, SimRng rng) {
        Rules.Meetings m = ctx.rules.meetings();
        boolean sameSect = p.sectId >= 0 && p.sectId == q.sectId;
        if (Combat.atWar(ctx, p, q)) {
            SectRelation war = ctx.sect(p.sectId).relations.get(q.sectId);
            Person[] order = strongerFirst(ctx, p, q);
            Combat.fight(ctx, order[0], order[1], Combat.Kind.WAR, war.causeEventId, "");
            return;
        }
        // Within one sect nobody fights across a great-realm gap, and a master does not brawl with
        // their own people (a contested succession is settled elsewhere).
        boolean mayFight = !sameSect
                || (p.realm == q.realm && !p.rank.equals("sect_master") && !q.rank.equals("sect_master"));
        Relation grudge = p.relation(q.id, Relation.ENEMY);
        if (grudge != null) {
            if (!mayFight) {
                return;
            }
            boolean stronger = Combat.winChance(ctx, p, q) >= 0.5;
            if (Revenge.isVendetta(grudge)) {
                if (Revenge.dares(ctx, p, q)) {
                    Combat.fight(ctx, p, q, Combat.Kind.REVENGE, grudge.causeEventId, grudge.reason);
                }
            } else if ((stronger || Revenge.reckless(ctx, p)) && rng.chance(m.duelChance() * p.aggression / 100.0)) {
                Combat.fight(ctx, p, q, Combat.Kind.DUEL, grudge.causeEventId, "");
            }
            return;
        }
        if (q.hasRelation(p.id, Relation.ENEMY)) {
            return;
        }
        double calm = (200 - p.aggression - q.aggression) / 200.0;
        double heat = (p.aggression + q.aggression) / 200.0;
        boolean prey = !sameSect && Combat.bestArtifact(q) != null && Combat.winChance(ctx, p, q) > 0.7;
        double[] w = {
            m.friend() * calm * (p.hasRelation(q.id, Relation.FRIEND) ? 2.0 : 1.0),
            m.quarrel() * heat,
            m.spar(),
            prey ? m.rob() * p.aggression / 100.0 * (100 - p.loyalty) / 100.0 : 0.0
        };
        switch (rng.weighted(w)) {
            case 0 -> {
                Relation existing = p.relation(q.id, Relation.FRIEND);
                if (existing == null && (friends(ctx, p) >= m.maxFriends() || friends(ctx, q) >= m.maxFriends())) {
                    return;
                }
                int strength = existing == null ? m.friendStrength() : Math.min(100, existing.strength + m.friendStrength() / 4);
                long id = existing != null ? -1 : ctx.chronicle.event("friendship", 1).actors(p.id, q.id)
                        .region(p.regionId).say(TextKeys.MEET_FRIEND, Anchor.of(ctx).who(p).who(q).region(p.regionId));
                p.relate(q.id, Relation.FRIEND, strength, id);
                q.relate(p.id, Relation.FRIEND, strength, id);
            }
            case 1 -> {
                // A quarrel is recorded only when it leaves a lasting enmity; most blow over.
                if (!rng.chance(m.grudgeChance() * heat * 2.0)) {
                    return;
                }
                long id = ctx.chronicle.event("quarrel", 1).actors(p.id, q.id).region(p.regionId)
                        .say(TextKeys.MEET_QUARREL, Anchor.of(ctx).who(p).who(q).region(p.regionId));
                p.relate(q.id, Relation.ENEMY, m.enemyStrength(), id, "self");
                q.relate(p.id, Relation.ENEMY, m.enemyStrength(), id, "self");
                // Fellow members settle a quarrel without blood; strangers may come to blows, the stronger striking.
                if (!sameSect && rng.chance(m.duelChance() * p.aggression / 100.0 * q.aggression / 100.0)) {
                    Person[] order = strongerFirst(ctx, p, q);
                    Combat.fight(ctx, order[0], order[1], Combat.Kind.DUEL, id, "");
                }
            }
            case 2 -> {
                // A friendly bout or a nod in passing leaves no mark worth recording.
            }
            case 3 -> {
                Boon prize = Combat.bestArtifact(q);
                Combat.fight(ctx, p, q, Combat.Kind.ROB, prize == null ? -1 : prize.sourceEventId, "");
            }
            default -> {
            }
        }
    }

    static int friends(SimContext ctx, Person p) {
        int n = 0;
        for (Relation r : p.relations) {
            if (r.kind.equals(Relation.FRIEND)) {
                n++;
            }
        }
        return n;
    }

    /** The two ordered so the stronger (by win chance) comes first, as the aggressor. */
    static Person[] strongerFirst(SimContext ctx, Person a, Person b) {
        return Combat.winChance(ctx, a, b) >= 0.5 ? new Person[] {a, b} : new Person[] {b, a};
    }
}
