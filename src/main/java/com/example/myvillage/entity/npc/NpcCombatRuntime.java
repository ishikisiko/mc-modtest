package com.example.myvillage.entity.npc;

import com.example.myvillage.combat.runtime.CombatActors;
import com.example.myvillage.combat.session.CombatSessionManager;
import com.example.myvillage.combat.session.CombatStopReason;
import com.example.myvillage.sim.PersonView;
import com.example.myvillage.sim.WorldSim;
import com.example.myvillage.sim.combat.NpcCombatProfile;
import com.example.myvillage.sim.runtime.WorldSimRuntime;
import com.example.myvillage.sim.runtime.WorldSimSavedData;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.slf4j.LoggerFactory;

/** Bounded index of loaded bodies. All durable mutations go through WorldSim, never entity NBT. */
public final class NpcCombatRuntime {
    private static final Map<UUID, NpcEntity> BODIES = new HashMap<>();
    private NpcCombatRuntime() {}
    public static NpcEntity find(UUID id) { return BODIES.get(id); }

    public static void equipInitial(NpcEntity npc) {
        if (!npc.getMainHandItem().isEmpty()) return;
        var id = ResourceLocation.parse(NpcCombatRules.get().weapon());
        if (!BuiltInRegistries.ITEM.containsKey(id)) throw new IllegalStateException("Missing NPC weapon " + id);
        npc.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(BuiltInRegistries.ITEM.get(id)));
        npc.setDropChance(EquipmentSlot.MAINHAND, 0); // equipment is persistent, not a respawn loot source
    }

    public static boolean bind(WorldSim sim, NpcEntity npc, PersonView person) {
        equipInitial(npc);
        var initial = new NpcCombatProfile(Math.max(1, npc.getMaxHealth() * (1 - person.injury() / 100.0)),
                npc.getMaxHealth(), NpcCombatRules.get().spirit(), weapon(npc), 0, 0, "", null);
        long epoch = sim.claimCombat(person.id(), initial);
        if (epoch < 0) return false;
        npc.combatEpoch = epoch;
        var profile = sim.combatProfile(person.id()).orElseThrow();
        try {
            npc.setItemSlot(EquipmentSlot.MAINHAND, profile.weapon().isEmpty() ? ItemStack.EMPTY
                    : ItemStack.parseOptional(npc.registryAccess(), TagParser.parseTag(profile.weapon())));
        } catch (Exception ex) {
            sim.releaseCombat(person.id(), epoch);
            throw new IllegalStateException("Invalid saved equipment for NPC " + person.id(), ex);
        }
        npc.setHealth((float) Math.min(npc.getMaxHealth(), profile.health()));
        npc.combatLastAttacker = profile.lastAttacker();
        var body = profile.body();
        if (body != null && (sim.life(person.id()).isEmpty() || unresolved(body))
                && body.region().equals(person.regionId())
                && body.dimension().equals(npc.level().dimension().location().toString())) {
            npc.setPos(body.x(), body.y(), body.z());
            npc.setDeltaMovement(body.velocityX(), body.velocityY(), body.velocityZ());
            npc.fallDistance = body.fallDistance();
            npc.setRemainingFireTicks(body.fireTicks());
        }
        CombatSessionManager.restoreRecovery(npc, profile.recoveryUntil());
        npc.nextRecoveryTick = Math.max(npc.level().getGameTime() + NpcCombatRules.get().recoveryDelayTicks(), profile.recoveryUntil());
        BODIES.put(npc.getUUID(), npc);
        dirty(npc);
        return true;
    }

    public static boolean managed(NpcEntity npc) {
        return BODIES.get(npc.getUUID()) == npc && (!npc.isLedgerAvatar()
                || WorldSimRuntime.sim().map(s -> s.ownsCombat(npc.ledgerPersonId(), npc.combatEpoch)).orElse(false));
    }

    public static boolean canTick(NpcEntity npc) {
        return npc.level() instanceof ServerLevel level && level.isPositionEntityTicking(npc.blockPosition())
                && !level.tickRateManager().isEntityFrozen(npc);
    }

    public static boolean unresolved(NpcCombatProfile.Body body) {
        return body.fallDistance() > 0 || body.velocityY() < -0.1 || body.fireTicks() > 0;
    }

    public static boolean canAct(NpcEntity npc) {
        return !npc.level().isClientSide() && npc.isAlive() && !npc.isRemoved() && managed(npc);
    }

    public static void joined(NpcEntity npc) {
        if (!npc.isLedgerAvatar()) BODIES.put(npc.getUUID(), npc);
    }

    public static void attacked(NpcEntity npc, DamageSource source) {
        if (!canAct(npc)) return;
        npc.combatLastAttacker = actor(source);
        npc.nextRecoveryTick = npc.level().getGameTime() + NpcCombatRules.get().recoveryDelayTicks();
        if (source.getEntity() instanceof LivingEntity attacker && CombatActors.mayHarm(npc, attacker)) {
            if (npc.getTarget() == null) {
                npc.combatAnchor = npc.position();
                npc.nextCombatDecision = npc.level().getGameTime() + NpcCombatRules.get().reactionTicks();
            }
            npc.setTarget(attacker);
            npc.lastThreatTick = npc.level().getGameTime();
            engage(npc, true);
        }
        checkpoint(npc);
    }

    public static void engage(NpcEntity npc, boolean fighting) {
        if (npc.isLedgerAvatar()) WorldSimRuntime.sim().ifPresent(s -> {
            if (s.engageCombat(npc.ledgerPersonId(), npc.combatEpoch, fighting)) dirty(npc);
        });
        npc.setCombatEngaged(fighting);
    }

    public static void disengage(NpcEntity npc) {
        CombatSessionManager.interruptNpc(npc, CombatStopReason.DISALLOWED, true);
        npc.setTarget(null); npc.getNavigation().stop();
        engage(npc, false);
        npc.nextRecoveryTick = npc.level().getGameTime() + NpcCombatRules.get().recoveryDelayTicks();
        checkpoint(npc);
    }

    public static void checkpoint(NpcEntity npc) {
        if (!npc.isLedgerAvatar() || !managed(npc)) return;
        WorldSimRuntime.sim().ifPresent(sim -> {
            var old = sim.combatProfile(npc.ledgerPersonId()).orElseThrow();
            var person = sim.person(npc.ledgerPersonId()).orElseThrow();
            Vec3 v = npc.getDeltaMovement();
            var body = new NpcCombatProfile.Body(npc.level().dimension().location().toString(), person.regionId(),
                    npc.getX(), npc.getY(), npc.getZ(), v.x, v.y, v.z, Math.max(0, npc.fallDistance), Math.max(0, npc.getRemainingFireTicks()));
            double health = Math.clamp(npc.getHealth(), 0, npc.getMaxHealth());
            String equipment = weapon(npc);
            long recovery = CombatSessionManager.recoveryUntil(npc);
            if (health == old.health() && npc.getMaxHealth() == old.maximumHealth() && equipment.equals(old.weapon())
                    && recovery == old.recoveryUntil() && body.equals(old.body()) && old.lastAttacker().equals(npc.combatLastAttacker)) return;
            if (sim.checkpointCombat(npc.ledgerPersonId(), npc.combatEpoch, new NpcCombatProfile(health,
                    npc.getMaxHealth(), old.spirit(), equipment, recovery, Math.incrementExact(old.revision()), npc.combatLastAttacker, body))) dirty(npc);
        });
    }

    public static void died(NpcEntity npc, DamageSource source) {
        if (!npc.isLedgerAvatar() || !managed(npc)) return;
        WorldSimRuntime.sim().ifPresent(sim -> {
            if (source.getEntity() instanceof ServerPlayer player)
                sim.registerPlayerActor(player.getUUID().toString(), player.getGameProfile().getName());
            String killer = actor(source);
            String name = source.getEntity() == null ? source.getMsgId() : source.getEntity().getName().getString();
            if (sim.combatDeath(npc.ledgerPersonId(), npc.combatEpoch, killer, name,
                    source.getMsgId(), NpcCombatRules.get().playerKillStandingPenalty())) {
                LoggerFactory.getLogger(NpcCombatRuntime.class).info("NPC_COMBAT death person={} epoch={} killer={} cause={}",
                        npc.ledgerPersonId(), npc.combatEpoch, killer, source.getMsgId());
                dirty(npc);
            }
        });
        CombatSessionManager.interruptNpc(npc, CombatStopReason.DEATH, false);
    }

    public static void leaving(NpcEntity npc) {
        if (npc.level().isClientSide()) return;
        checkpoint(npc);
        if (npc.isLedgerAvatar()) WorldSimRuntime.sim().ifPresent(sim -> {
            if (sim.releaseCombat(npc.ledgerPersonId(), npc.combatEpoch)) dirty(npc);
        });
        CombatSessionManager.interruptNpc(npc, CombatStopReason.DISALLOWED, false);
        CombatSessionManager.removeRuntime(npc.getUUID(), true);
        BODIES.remove(npc.getUUID(), npc);
    }

    public static void tick(MinecraftServer server) {
        for (NpcEntity npc : BODIES.values().toArray(NpcEntity[]::new)) {
            if (npc.isRemoved()) { leaving(npc); continue; }
            if (!canAct(npc) || !canTick(npc)) continue;
            if (npc.getTarget() == null && npc.onGround() && !npc.isOnFire() && !npc.isInWater()
                    && npc.level().getGameTime() >= npc.nextRecoveryTick) {
                npc.nextRecoveryTick = npc.level().getGameTime() + NpcCombatRules.get().healIntervalTicks();
                npc.heal((float) (npc.getMaxHealth() * NpcCombatRules.get().healFraction()));
            }
            checkpoint(npc);
        }
    }

    public static void clear() { BODIES.clear(); }
    private static String weapon(NpcEntity npc) {
        return npc.getMainHandItem().isEmpty() ? "" : npc.getMainHandItem().save(npc.registryAccess()).toString();
    }
    private static String actor(DamageSource source) {
        var entity = source.getEntity();
        if (entity instanceof ServerPlayer player) return "player:" + player.getUUID();
        if (entity instanceof NpcEntity npc && npc.isLedgerAvatar()) return "npc:" + npc.ledgerPersonId();
        return entity == null ? "environment:" + source.getMsgId() : "entity:" + BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
    }
    private static void dirty(NpcEntity npc) {
        if (npc.level() instanceof ServerLevel level) WorldSimSavedData.get(level.getServer().overworld()).setDirty();
    }
}
