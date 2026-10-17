package com.example.myvillage.combat.runtime;

import com.example.myvillage.entity.npc.NpcEntity;
import com.example.myvillage.sim.runtime.WorldSimRuntime;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** Body/source adapter and shared damage permission. AI choices never override this policy. */
public final class CombatActors {
    private CombatActors() {}

    public static DamageSource source(LivingEntity actor) {
        return actor instanceof Player player ? actor.damageSources().playerAttack(player)
                : actor.damageSources().mobAttack(actor);
    }

    public static boolean mayHarm(LivingEntity actor, Entity target) {
        if (actor == target || actor.level() != target.level() || !actor.isAlive() || !target.isAlive()
                || target.isSpectator() || actor.isAlliedTo(target) || target.isAlliedTo(actor)) return false;
        if (target instanceof Player player && player.isCreative()) return false;
        if (actor instanceof Player player && target instanceof Player other && !player.canHarmPlayer(other)) return false;
        var sim = WorldSimRuntime.sim().orElse(null);
        if (sim == null) return true;
        int a = sect(actor, sim), b = sect(target, sim);
        return a < 0 || a != b;
    }

    private static int sect(Entity entity, com.example.myvillage.sim.WorldSim sim) {
        if (entity instanceof NpcEntity npc && npc.isLedgerAvatar())
            return sim.person(npc.ledgerPersonId()).map(p -> p.sectId()).orElse(-1);
        if (entity instanceof ServerPlayer player)
            return sim.actor(com.example.myvillage.sim.social.ActorRef.player(player.getUUID().toString()))
                    .map(a -> a.sectId()).orElse(-1);
        return -1;
    }
}
