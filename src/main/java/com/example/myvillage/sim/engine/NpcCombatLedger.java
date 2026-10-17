package com.example.myvillage.sim.engine;

import com.example.myvillage.sim.combat.NpcCombatProfile;
import com.example.myvillage.sim.model.Person;
import com.example.myvillage.sim.model.Relation;
import com.example.myvillage.sim.social.ActorRef;
import com.example.myvillage.sim.life.LifeEngine;

/** Actual outcomes enter here; no dice reroll a fight witnessed in the world. */
public final class NpcCombatLedger {
    private NpcCombatLedger() {}

    public static long claim(SimContext ctx, int id, NpcCombatProfile initial) {
        if (!ctx.state.persons.containsKey(id) || ctx.state.combat.leases.containsKey(id)) return -1;
        ctx.state.combat.profiles.putIfAbsent(id, initial);
        // A zero-health receipt must never be reinterpreted as an uninitialized person.
        if (ctx.state.combat.profiles.get(id).health() <= 0) return -1;
        long epoch = ctx.state.combat.nextEpoch;
        ctx.state.combat.nextEpoch = Math.incrementExact(epoch);
        ctx.state.combat.leases.put(id, epoch);
        return epoch;
    }

    public static boolean owns(SimContext ctx, int id, long epoch) {
        return epoch > 0 && ctx.state.persons.containsKey(id)
                && ctx.state.combat.leases.getOrDefault(id, -1L) == epoch;
    }

    public static boolean checkpoint(SimContext ctx, int id, long epoch, NpcCombatProfile update) {
        if (!owns(ctx, id, epoch)) return false;
        NpcCombatProfile old = ctx.state.combat.profiles.get(id);
        if (update.revision() <= old.revision()) return false;
        ctx.state.combat.profiles.put(id, update);
        Person p = ctx.state.persons.get(id);
        p.injury = Math.clamp((int) Math.round(100 * (1 - update.health() / update.maximumHealth())), 0, 100);
        p.injurerId = update.lastAttacker().startsWith("npc:") ? ActorRef.parse(update.lastAttacker()).personId() : -1;
        return true;
    }

    public static boolean engage(SimContext ctx, int id, long epoch, boolean fighting) {
        if (!owns(ctx, id, epoch)) return false;
        boolean changed = fighting ? ctx.state.combat.engaged.add(id) : ctx.state.combat.engaged.remove(id);
        if (changed) LifeEngine.combat(ctx, id, fighting);
        return changed;
    }

    public static boolean release(SimContext ctx, int id, long epoch) {
        if (!owns(ctx, id, epoch)) return false;
        engage(ctx, id, epoch, false);
        ctx.state.combat.leases.remove(id);
        return true;
    }

    /** Cold injuries/healing update the same saved health; no new body resets them. */
    public static void syncInjury(SimContext ctx, Person p) {
        if (ctx.state.combat.leases.containsKey(p.id)) return;
        NpcCombatProfile old = ctx.state.combat.profiles.get(p.id);
        if (old == null) return;
        double health = Math.max(Math.min(1, old.maximumHealth()), old.maximumHealth() * (1 - p.injury / 100.0));
        if (Math.abs(health - old.health()) < 0.000001) return;
        ctx.state.combat.profiles.put(p.id, new NpcCombatProfile(health, old.maximumHealth(), old.spirit(),
                old.weapon(), old.recoveryUntil(), Math.incrementExact(old.revision()), old.lastAttacker(), old.lastDamageTick(), old.body()));
    }

    public static boolean die(SimContext ctx, int id, long epoch, String killer, String killerName,
            String cause, int playerStandingPenalty) {
        if (!owns(ctx, id, epoch)) return false;
        if (cause == null || cause.length() > 128 || killerName == null || killerName.length() > 256)
            throw new IllegalArgumentException("invalid_combat_death");
        ActorRef actor = killer.startsWith("npc:") || killer.startsWith("player:") ? ActorRef.parse(killer) : null;
        Person victim = ctx.state.persons.get(id);
        var message = ctx.chronicle.event("slain", Math.max(2, Deaths.importance(ctx, victim)))
                .actors(id).region(victim.regionId);
        String causeWord = "@commands.myvillage.world.cause." + cause;
        long event = killerName.isEmpty()
                ? message.say(TextKeys.P + "near_death_environment", victim.name(), causeWord)
                : message.say(TextKeys.P + "near_death", victim.name(), killerName, causeWord);
        Person npcKiller = actor != null && actor.kind() == ActorRef.Kind.NPC
                ? ctx.state.persons.get(actor.personId()) : null;
        if (npcKiller != null && npcKiller != victim) {
            Combat.afterKill(ctx, npcKiller, victim, event);
        } else {
            if (actor != null && actor.kind() == ActorRef.Kind.PLAYER) {
                for (Person other : ctx.state.persons.values()) {
                    boolean avenges = other.id == victim.masterId || other.masterId == victim.id
                            || (victim.rank.equals("sect_master") && other.sectId == victim.sectId && other.rank.equals("elder"));
                    for (Relation relation : victim.relations)
                        if (relation.other == other.id && relation.kind.equals(Relation.FRIEND)
                                && relation.strength >= ctx.rules.revenge().friendMinStrength()) avenges = true;
                    if (avenges && other.id != victim.id)
                        ctx.state.combat.grievances.computeIfAbsent(other.id, ignored -> new java.util.TreeMap<>())
                                .put(actor.stableKey(), event);
                }
                if (victim.sectId >= 0) ctx.state.combat.sectGrievances
                        .computeIfAbsent(victim.sectId, ignored -> new java.util.TreeMap<>()).put(actor.stableKey(), event);
                var member = ctx.state.playerMembers.get(actor.playerId());
                if (member != null && victim.sectId >= 0)
                    PlayerAffairs.addStanding(member, victim.sectId, -playerStandingPenalty);
            }
            Deaths.bury(ctx, victim, cause, actor != null && actor.kind() == ActorRef.Kind.NPC ? actor.personId() : -1, event);
        }
        var tomb = ctx.state.tombstones.get(id);
        tomb.killerActor = killer;
        tomb.killerName = killerName;
        tomb.combatEpoch = epoch;
        PlayerAffairs.daily(ctx);
        Succession.settle(ctx);
        ctx.chronicle.endDay();
        return true;
    }
}
