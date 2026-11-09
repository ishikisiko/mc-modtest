package com.example.myvillage.cultivation.fate;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.cultivation.CultivationService;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class FateRuntime {
    private static final ResourceLocation HEALTH = ResourceLocation.fromNamespaceAndPath(MyVillageMod.MOD_ID, "fate_health");
    private static final ResourceLocation SPEED = ResourceLocation.fromNamespaceAndPath(MyVillageMod.MOD_ID, "fate_speed");
    private FateRuntime() { }
    public static void register() {
        HerbalLoreRuntime.register();
        ElementAffinityRuntime.register();
        JingangRuntime.register();
        PhoenixRuntime.register();
        com.example.myvillage.entity.npc.NpcPhoenixRuntime.register();
        SpiritBodyRuntime.register();
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, FateRuntime::incoming);
        NeoForge.EVENT_BUS.addListener(FateRuntime::damaged);
        NeoForge.EVENT_BUS.addListener(FateRuntime::drops);
        NeoForge.EVENT_BUS.addListener(FateRuntime::fall);
        NeoForge.EVENT_BUS.addListener(FateRuntime::knockback);
        NeoForge.EVENT_BUS.addListener(FateRuntime::tick);
    }
    private static void replace(AttributeInstance attribute, ResourceLocation id, double amount) {
        if (attribute == null) return;
        var old = attribute.getModifier(id);
        if (old != null && old.amount() == amount) return;
        attribute.removeModifier(id);
        if (amount != 0) attribute.addPermanentModifier(new AttributeModifier(id, amount,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }
    public static void refresh(ServerPlayer player) {
        CultivationService.clampSpiritualPower(player);
        float oldHealth = player.getHealth();
        replace(player.getAttribute(Attributes.MAX_HEALTH), HEALTH, FateService.effect(player, "health"));
        SpiritBodyAttributes.refresh(player);
        EverlastingRuntime.refresh(player);
        PhoenixRuntime.refresh(player);
        double speed = FateService.effect(player, "speed");
        if (FateEffects.low(player.getHealth(), player.getMaxHealth(), FateService.effect(player, "low_speed_threshold")))
            speed += FateService.effect(player, "low_speed");
        replace(player.getAttribute(Attributes.MOVEMENT_SPEED), SPEED, speed);
        if (oldHealth > player.getMaxHealth()) player.setHealth(player.getMaxHealth());
    }
    public static void refresh(LivingEntity actor) {
        if (actor instanceof ServerPlayer player) { refresh(player); return; }
        if (!FateActors.supported(actor)) return;
        float oldHealth=actor.getHealth();
        replace(actor.getAttribute(Attributes.MAX_HEALTH),HEALTH,FateService.effect(actor,"health"));
        SpiritBodyAttributes.refresh(actor);
        double speed=FateService.effect(actor,"speed");
        if(FateEffects.low(actor.getHealth(),actor.getMaxHealth(),FateService.effect(actor,"low_speed_threshold")))
            speed+=FateService.effect(actor,"low_speed");
        replace(actor.getAttribute(Attributes.MOVEMENT_SPEED),SPEED,speed);
        if(oldHealth>actor.getMaxHealth())actor.setHealth(actor.getMaxHealth());
    }
    private static boolean hostile(net.minecraft.world.entity.LivingEntity target, LivingEntity player) {
        return target instanceof Enemy && !target.isAlliedTo(player);
    }
    private static boolean personallyCaused(net.minecraft.world.damagesource.DamageSource source, LivingEntity player) {
        return source.getDirectEntity() == player
                || (source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.Projectile projectile
                    && projectile.getOwner() == player);
    }
    private static void incoming(LivingIncomingDamageEvent event) {
        if (event.getEntity().level().isClientSide() || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        var source = event.getSource(); double damage = event.getAmount();
        if (source.getEntity() instanceof LivingEntity attacker && FateActors.supported(attacker)
                && !com.example.myvillage.combat.runtime.CombatDamageService.isDerivedHit(event.getEntity())) {
            // Authored attacks/projectiles only; environmental fire has no player source.
            boolean direct = personallyCaused(source, attacker);
            damage *= FateEffects.damageFactor(direct ? FateService.effect(attacker, "damage_bonus") : 0,
                    FateEffects.elementBonus(com.example.myvillage.stats.runtime.StatsRuntime.mappedType(source)
                            .map(type -> type.id()).orElse(""), source.is(DamageTypeTags.IS_FIRE),
                            com.example.myvillage.combat.runtime.CombatDamageService.isResolvingElementalAffinity(event.getEntity()),
                            effect -> FateService.effect(attacker,effect)),
                    direct && hostile(event.getEntity(), attacker) ? FateService.effect(attacker, "hunter_bonus") : 0);
        }
        if (FateActors.supported(event.getEntity())) {
            LivingEntity target=event.getEntity();
            if (source.is(DamageTypeTags.IS_FIRE) && source.getEntity() == null && source.getDirectEntity() == null)
                damage *= 1 - FateService.effect(target, "environment_fire_reduction");
            if (FateEffects.low(target.getHealth(), target.getMaxHealth(), FateService.effect(target, "low_threshold")))
                damage *= 1 - FateService.effect(target, "low_reduction");
        }
        event.setAmount((float) Math.max(0, damage));
    }
    private static void damaged(LivingDamageEvent.Post event) {
        LivingEntity player=event.getEntity();
        if (!FateActors.valid(player) || event.getNewDamage() <= 0) return;
        FateState state = FateService.state(player);
        FateState next = FateEffects.beginRecovery(state, player.getServer().overworld().getGameTime(),
                player.getHealth(), player.getMaxHealth(), FateService.effect(player, "recovery_threshold"),
                FateService.effect(player, "recovery_fraction"), (int) FateService.effect(player, "recovery_ticks"),
                (int) FateService.effect(player, "recovery_cooldown"));
        if (!state.equals(next)) CultivationService.replaceFates(player, next, false);
    }
    /** Drops happen only after death was accepted, so canceled death never rewards the killer. */
    private static void drops(LivingDropsEvent event) {
        if (com.example.myvillage.combat.runtime.CombatDamageService.isDerivedHit(event.getEntity())) return;
        if (!(event.getSource().getEntity() instanceof LivingEntity player) || !FateActors.valid(player) || !hostile(event.getEntity(), player)
                || !personallyCaused(event.getSource(), player)) return;
        // Pet/summon sources identify their own entity, not their owner.
        FateState state = FateService.state(player); long now = player.getServer().overworld().getGameTime();
        double fraction = FateService.effect(player, "hunter_recovery");
        if (fraction <= 0 || now < state.hunterReadyAt()) return;
        long amount = FateEffects.recoveredPower(FateActors.powerCap(player), fraction);
        if (amount > 0 && FateActors.recoverPower(player, amount)) {
            CultivationService.replaceFates(player, state.timers(state.recoveryReadyAt(), state.healingTicks(),
                    state.healingPerTick(), now + (long) FateService.effect(player, "hunter_cooldown")), false);
        }
    }
    private static void fall(LivingFallEvent event) {
        LivingEntity player=event.getEntity();
        if (FateActors.valid(player))
            event.setDamageMultiplier((float) (event.getDamageMultiplier() * (1 - FateService.effect(player, "fall_reduction"))));
    }
    private static void knockback(LivingKnockBackEvent event) {
        LivingEntity player=event.getEntity();
        if (!FateActors.valid(player)) return;
        double factor = 1 - FateService.effect(player, "knockback");
        factor *= groundKnockbackFactor(player);
        if (FateEffects.low(player.getHealth(), player.getMaxHealth(), FateService.effect(player, "low_threshold")))
            factor *= 1 - FateService.effect(player, "low_knockback");
        event.setStrength((float) (event.getStrength() * factor));
    }
    public static double groundKnockbackFactor(net.minecraft.world.entity.LivingEntity target) {
        return FateActors.supported(target)
                ? FateEffects.groundKnockbackFactor(target instanceof ServerPlayer player ? WindStepRuntime.grounded(player) : target.onGround(),FateService.effect(target,"ground_knockback")) : 1;
    }
    public static void respawn(ServerPlayer player) {
        SwordObsessionRuntime.forget(player);
        FateState s = FateService.state(player);
        CultivationService.replaceFates(player, s.afterDeath(), false);
    }
    private static void tick(ServerTickEvent.Post event) {
        WindStepRuntime.tick(event.getServer());
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            refresh(player);
            tickActor(player);
        }
    }
    public static void tickActor(LivingEntity actor) {
        FateState state=FateService.state(actor);
        if(state.healingTicks()<=0||!actor.isAlive())return;
        if(FateService.effect(actor,"recovery_fraction")>0)actor.heal((float)state.healingPerTick());
        CultivationService.replaceFates(actor,state.timers(state.recoveryReadyAt(),state.healingTicks()-1,
                state.healingPerTick(),state.hunterReadyAt()),false);
    }
}
