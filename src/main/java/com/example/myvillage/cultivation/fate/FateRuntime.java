package com.example.myvillage.cultivation.fate;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.cultivation.CultivationService;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
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
        double speed = FateService.effect(player, "speed");
        if (FateEffects.low(player.getHealth(), player.getMaxHealth(), FateService.effect(player, "low_speed_threshold")))
            speed += FateService.effect(player, "low_speed");
        replace(player.getAttribute(Attributes.MOVEMENT_SPEED), SPEED, speed);
        if (oldHealth > player.getMaxHealth()) player.setHealth(player.getMaxHealth());
    }
    private static boolean hostile(net.minecraft.world.entity.LivingEntity target, ServerPlayer player) {
        return target instanceof Enemy && !target.isAlliedTo(player);
    }
    private static boolean personallyCaused(net.minecraft.world.damagesource.DamageSource source, ServerPlayer player) {
        return source.getDirectEntity() == player
                || (source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.Projectile projectile
                    && projectile.getOwner() == player);
    }
    private static void incoming(LivingIncomingDamageEvent event) {
        if (event.getEntity().level().isClientSide() || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        var source = event.getSource(); double damage = event.getAmount();
        if (source.getEntity() instanceof ServerPlayer attacker
                && !com.example.myvillage.combat.runtime.CombatDamageService.isDerivedHit(event.getEntity())) {
            // Authored attacks/projectiles only; environmental fire has no player source.
            boolean direct = personallyCaused(source, attacker);
            damage *= FateEffects.damageFactor(direct ? FateService.effect(attacker, "damage_bonus") : 0,
                    source.is(DamageTypeTags.IS_FIRE) ? FateService.effect(attacker, "fire_bonus") : 0,
                    direct && hostile(event.getEntity(), attacker) ? FateService.effect(attacker, "hunter_bonus") : 0);
        }
        if (event.getEntity() instanceof ServerPlayer target) {
            if (source.is(DamageTypeTags.IS_FIRE) && source.getEntity() == null && source.getDirectEntity() == null)
                damage *= 1 - FateService.effect(target, "environment_fire_reduction");
            if (FateEffects.low(target.getHealth(), target.getMaxHealth(), FateService.effect(target, "low_threshold")))
                damage *= 1 - FateService.effect(target, "low_reduction");
        }
        event.setAmount((float) Math.max(0, damage));
    }
    private static void damaged(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getNewDamage() <= 0 || !player.isAlive()) return;
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
        if (!(event.getSource().getEntity() instanceof ServerPlayer player) || !hostile(event.getEntity(), player)
                || !personallyCaused(event.getSource(), player)) return;
        // Pet/summon sources identify their own entity, not their owner.
        FateState state = FateService.state(player); long now = player.getServer().overworld().getGameTime();
        double fraction = FateService.effect(player, "hunter_recovery");
        if (fraction <= 0 || now < state.hunterReadyAt()) return;
        long amount = FateEffects.recoveredPower(CultivationService.spiritualPowerCap(player), fraction);
        if (amount > 0 && CultivationService.recoverSpiritualPower(player, amount).success()) {
            CultivationService.replaceFates(player, state.timers(state.recoveryReadyAt(), state.healingTicks(),
                    state.healingPerTick(), now + (long) FateService.effect(player, "hunter_cooldown")), false);
        }
    }
    private static void fall(LivingFallEvent event) {
        if (event.getEntity() instanceof ServerPlayer player)
            event.setDamageMultiplier((float) (event.getDamageMultiplier() * (1 - FateService.effect(player, "fall_reduction"))));
    }
    private static void knockback(LivingKnockBackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        double factor = 1 - FateService.effect(player, "knockback");
        if (FateEffects.low(player.getHealth(), player.getMaxHealth(), FateService.effect(player, "low_threshold")))
            factor *= 1 - FateService.effect(player, "low_knockback");
        event.setStrength((float) (event.getStrength() * factor));
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
            FateState state = FateService.state(player);
            if (state.healingTicks() <= 0 || !player.isAlive()) continue;
            // No catch-up on login. Timer state is saved, and only an actual server tick heals.
            if (FateService.effect(player, "recovery_fraction") > 0) player.heal((float) state.healingPerTick());
            CultivationService.replaceFates(player, state.timers(state.recoveryReadyAt(), state.healingTicks() - 1,
                    state.healingPerTick(), state.hunterReadyAt()), false);
        }
    }
}
