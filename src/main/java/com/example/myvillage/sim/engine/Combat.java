package com.example.myvillage.sim.engine;

import com.example.myvillage.sim.SimRng;
import com.example.myvillage.sim.data.ContentTables;
import com.example.myvillage.sim.data.Rules;
import com.example.myvillage.sim.model.Boon;
import com.example.myvillage.sim.model.Person;
import com.example.myvillage.sim.model.Relation;
import com.example.myvillage.sim.model.SectRelation;

/**
 * Fights between people (design §4.5–4.6). Power = stage power × technique × best artifact ×
 * injury; the win chance is steep in the noisy power ratio, so beating a higher great realm is rare
 * and upsets within a realm are ordinary. The loser flees, is wounded or is killed. A kill names
 * the killer, turns the victim's master, disciples and close friends into avengers whose enmity
 * points at the kill, and sours the standing between the two sects.
 */
public final class Combat {
    private Combat() {
    }

    /** Why a fight happens; decides how deadly it is and how the line reads. */
    public enum Kind {
        DUEL(1), ROB(2), REVENGE(3), WAR(4), CHAMPION(5), CONTEST(6), SUCCESSION(7);

        /** Explicit salt for the fight's randomness (never the ordinal). */
        final int code;

        Kind(int code) {
            this.code = code;
        }
    }

    /** Outcome: the winner, the loser and whether the loser died. */
    public record Result(Person winner, Person loser, boolean killed, long eventId) {
    }

    public static double power(SimContext ctx, Person p) {
        Rules.Combat c = ctx.rules.combat();
        double power = ctx.realm(p).stage(p.stage).power();
        ContentTables.Technique t = ctx.technique(p);
        power *= t == null ? ctx.rules.techniques().none().combat() : ctx.rules.techniques().grades().get(t.grade()).combat();
        power *= artifactFactor(ctx, p);
        power *= Math.max(c.injuryFloor(), 1.0 - p.injury * c.injuryPerPoint());
        return power;
    }

    static double artifactFactor(SimContext ctx, Person p) {
        double best = 1.0;
        for (Boon b : p.boons) {
            if (b.kind.equals(Boon.ARTIFACT)) {
                best = Math.max(best, b.amount);
            }
        }
        return best;
    }

    static Boon bestArtifact(Person p) {
        Boon best = null;
        for (Boon b : p.boons) {
            if (b.kind.equals(Boon.ARTIFACT) && (best == null || b.amount > best.amount)) {
                best = b;
            }
        }
        return best;
    }

    /** Chance that {@code a} beats {@code b} without noise. */
    public static double winChance(SimContext ctx, Person a, Person b) {
        double k = ctx.rules.combat().steepness();
        double pa = StrictMath.pow(power(ctx, a), k);
        double pb = StrictMath.pow(power(ctx, b), k);
        return pa / (pa + pb);
    }

    /**
     * Resolves a fight started by {@code a}.
     *
     * @param causeId the event that led to it (an insult, a kill to avenge, a war, a fortune)
     * @param detail  the item fought over (contest, rob) or the revenge reason; "" otherwise
     */
    public static Result fight(SimContext ctx, Person a, Person b, Kind kind, long causeId, String detail) {
        Rules.Combat c = ctx.rules.combat();
        SimRng rng = ctx.rng(a.id, Purpose.COMBAT_OUTCOME, b.id * 31L + kind.code);
        double k = c.steepness();
        double pa = StrictMath.pow(power(ctx, a) * rng.uniform(1.0 - c.noise(), 1.0 + c.noise()), k);
        double pb = StrictMath.pow(power(ctx, b) * rng.uniform(1.0 - c.noise(), 1.0 + c.noise()), k);
        boolean aWins = rng.nextDouble() < pa / (pa + pb);
        Person winner = aWins ? a : b;
        Person loser = aWins ? b : a;

        double kill = c.killBase();
        if (winner.hasRelation(loser.id, Relation.ENEMY) || kind == Kind.REVENGE) {
            kill += c.killEnemy();
        }
        if (kind == Kind.WAR || kind == Kind.CHAMPION) {
            kill += c.killWar();
        }
        kill *= 0.5 + winner.aggression / 100.0;
        if (winner.sectId >= 0 && winner.sectId == loser.sectId && kind != Kind.SUCCESSION) {
            kill *= c.sameSectKillFactor();
        }
        double flee = c.fleeBase() * (0.5 + loser.caution / 100.0);
        if (kind == Kind.CHAMPION || kind == Kind.SUCCESSION) {
            flee = 0.0;
        }
        if (kind == Kind.ROB && winner == a) {
            loot(ctx, winner, loser, rng);
        }
        if (rng.chance(kill)) {
            long id = slay(ctx, winner, loser, kind, causeId, detail, a);
            return new Result(winner, loser, true, id);
        }
        if (rng.chance(flee)) {
            long id = ctx.chronicle.event("fight", 1).actors(winner.id, loser.id).region(a.regionId).cause(causeId)
                    .say(TextKeys.FIGHT_FLEE, Anchor.of(ctx).who(winner).who(loser).region(a.regionId));
            loser.relate(winner.id, Relation.ENEMY, ctx.rules.meetings().enemyStrength() / 2, id, "self");
            return new Result(winner, loser, false, id);
        }
        int wound = rng.range(c.wound()[0], c.wound()[1]);
        Chronicle.Builder event = ctx.chronicle.event(kind == Kind.CHAMPION ? "battle" : "fight",
                kind == Kind.CHAMPION ? 3 : 1).actors(winner.id, loser.id).region(a.regionId).cause(causeId);
        long id;
        if (kind == Kind.CHAMPION) {
            id = event.sects(sectsOf(winner, loser)).say(detail.equals("again") ? TextKeys.WAR_CLASH_AGAIN : TextKeys.WAR_CLASH,
                    Anchor.of(ctx).add(ctx.sectName(winner.sectId), ctx.sectName(loser.sectId))
                            .region(a.regionId).who(winner).who(loser));
        } else if (kind == Kind.REVENGE && winner == a) {
            id = event.say(TextKeys.FIGHT_REVENGE_WOUND,
                    Anchor.of(ctx).who(winner).who(loser).region(a.regionId).add(TextKeys.reason(detail)));
        } else {
            id = event.say(TextKeys.FIGHT_WOUND, Anchor.of(ctx).who(winner).who(loser).region(a.regionId));
        }
        loser.injury = Math.min(100, loser.injury + wound);
        loser.injuryEventId = id;
        loser.injurerId = winner.id;
        loser.relate(winner.id, Relation.ENEMY, ctx.rules.meetings().enemyStrength(), id, "self");
        SectPolitics.incident(ctx, winner, loser, id, false);
        return new Result(winner, loser, false, id);
    }

    private static Integer[] sectsOf(Person a, Person b) {
        if (a.sectId >= 0 && b.sectId >= 0 && a.sectId != b.sectId) {
            return new Integer[] {a.sectId, b.sectId};
        }
        if (a.sectId >= 0) {
            return new Integer[] {a.sectId};
        }
        return b.sectId >= 0 ? new Integer[] {b.sectId} : new Integer[0];
    }

    private static void loot(SimContext ctx, Person winner, Person loser, SimRng rng) {
        Boon prize = bestArtifact(loser);
        if (prize != null && rng.chance(ctx.rules.combat().lootChance())) {
            loser.boons.remove(prize);
            winner.boons.add(prize);
        }
    }

    /** Whether a person counts as notable for importance (elder and up, or the notable realm). */
    public static boolean notable(SimContext ctx, Person p) {
        return p.rank.equals("sect_master") || p.rank.equals("elder") || SectAffairs.reached(ctx, p,
                ctx.rules.importance().notable());
    }

    /** Emits the kill as the victim's death, then settles its consequences. */
    private static long slay(SimContext ctx, Person killer, Person victim, Kind kind, long causeId, String detail,
                             Person initiator) {
        String region = initiator.regionId;
        boolean notableEither = notable(ctx, victim) || notable(ctx, killer);
        int importance = Math.max(2, Deaths.importance(ctx, victim));
        String family;
        Anchor params = Anchor.of(ctx).who(victim).who(killer);
        switch (kind) {
            case ROB -> {
                Boon prize = bestArtifact(killer);
                var art = prize == null ? null : ctx.data.artifact(prize.refId);
                if (art == null || killer != initiator) {
                    family = TextKeys.SLAIN + "duel";
                    params.region(region);
                } else {
                    family = TextKeys.SLAIN + "rob";
                    params.region(region).add(art.name());
                }
            }
            case REVENGE -> {
                var wrong = ctx.findEvent(causeId);
                boolean late = wrong != null
                        && ctx.day() - wrong.day() >= (long) ctx.rules.revenge().lateYears() * ctx.dpy;
                family = TextKeys.SLAIN + (killer != initiator ? "revenge_failed" : late ? "revenge_late" : "revenge");
                params.region(region).add(TextKeys.reason(detail));
                if (notableEither) {
                    importance = 3;
                }
            }
            case WAR -> {
                family = TextKeys.SLAIN + "war";
                params.region(region);
            }
            case CHAMPION -> {
                family = detail.equals("again") ? TextKeys.SLAIN_BATTLE_AGAIN : TextKeys.SLAIN_BATTLE;
                params = Anchor.of(ctx).add(ctx.sectName(killer.sectId), ctx.sectName(victim.sectId))
                        .region(region).who(victim).who(killer);
                importance = 3;
            }
            case CONTEST -> {
                family = TextKeys.SLAIN + "contest";
                params.region(region).add(detail);
            }
            case SUCCESSION -> {
                family = TextKeys.SLAIN + "succession";
                importance = 3;
            }
            default -> {
                family = TextKeys.SLAIN + "duel";
                params.region(region);
            }
        }
        long id = ctx.chronicle.event("slain", importance).actors(victim.id, killer.id)
                .sects(sectsOf(victim, killer)).region(region).cause(causeId).say(family, params);
        afterKill(ctx, killer, victim, id);
        return id;
    }

    /** Avengers, sect standing, the killer's tally; then the victim is buried. */
    static void afterKill(SimContext ctx, Person killer, Person victim, long killEventId) {
        Rules.Revenge r = ctx.rules.revenge();
        killer.kills++;
        killer.forget(victim.id, Relation.ENEMY);
        if (killer.quarryId == victim.id) {
            killer.quarryId = -1;
        }
        Person master = victim.masterId >= 0 ? ctx.state.persons.get(victim.masterId) : null;
        if (master != null && master != killer) {
            master.relate(killer.id, Relation.ENEMY, r.strength(), killEventId, "disciple");
        }
        for (Relation rel : victim.relations) {
            Person other = ctx.state.persons.get(rel.other);
            if (other == null || other == killer) {
                continue;
            }
            if (rel.kind.equals(Relation.DISCIPLE)) {
                other.relate(killer.id, Relation.ENEMY, r.strength(), killEventId, "master");
            } else if (rel.kind.equals(Relation.FRIEND) && rel.strength >= r.friendMinStrength()) {
                other.relate(killer.id, Relation.ENEMY, r.strength(), killEventId, "friend");
            }
        }
        if (victim.rank.equals("sect_master") && victim.sectId != killer.sectId) {
            for (Person m : ctx.members(victim.sectId)) {
                if (m != victim && m.rank.equals("elder")) {
                    m.relate(killer.id, Relation.ENEMY, r.strength(), killEventId, "sect");
                }
            }
        }
        SectPolitics.incident(ctx, killer, victim, killEventId, true);
        Deaths.bury(ctx, victim, Deaths.SLAIN, killer.id, killEventId);
    }

    /** Sect-relation shortcut used by meetings: are the two at war? */
    static boolean atWar(SimContext ctx, Person a, Person b) {
        if (a.sectId < 0 || b.sectId < 0 || a.sectId == b.sectId) {
            return false;
        }
        SectRelation rel = ctx.sect(a.sectId).relations.get(b.sectId);
        return rel != null && rel.state.equals(SectRelation.WAR);
    }
}
