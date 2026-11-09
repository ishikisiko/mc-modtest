package com.example.myvillage.entity.npc;

import com.example.myvillage.cultivation.fate.*;
import com.example.myvillage.combat.foundation.HealingMath;
import com.example.myvillage.combat.foundation.HealingRuntime;
import com.example.myvillage.cultivation.CultivationService;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.*;

/** NPC 的凤凰持久化与身体适配；火种和凝聚规则与玩家共用。 */
public final class NpcPhoenixRuntime {
    private static final Gson GSON=new Gson();
    private static final String EMBER="phoenix_ember",PROTECTED="phoenix_protected_until";
    private static final ResourceLocation HEAL=ResourceLocation.parse("myvillage:phoenix_self_healing");
    private NpcPhoenixRuntime() { }
    public static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST,NpcPhoenixRuntime::incoming);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,NpcPhoenixRuntime::death);
        NeoForge.EVENT_BUS.addListener((LivingHealEvent e)->{if(e.getEntity() instanceof NpcEntity npc&&active(npc))e.setCanceled(true);});
    }
    public static boolean active(NpcEntity npc) { return npc.innateFates()!=null&&NpcFateRuntime.extras(npc).has(EMBER); }
    private static PhoenixEmberClock clock(NpcEntity npc) {
        var json=NpcFateRuntime.extras(npc);return json.has(EMBER)?GSON.fromJson(json.getAsJsonObject(EMBER).get("clock"),PhoenixEmberClock.class):null;
    }
    private static void incoming(LivingIncomingDamageEvent event) {
        if(!(event.getEntity() instanceof NpcEntity npc)||npc.level().isClientSide()||event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY))return;
        var clock=clock(npc);var extras=NpcFateRuntime.extras(npc);
        if(clock!=null&&(clock.protectionRemaining()>0||event.getSource().is(DamageTypes.DROWN)
                ||event.getSource().is(DamageTypes.ON_FIRE)||event.getSource().is(DamageTypes.WITHER)))event.setCanceled(true);
        else if(extras.has(PROTECTED)&&FateActors.now(npc)<extras.get(PROTECTED).getAsLong())event.setCanceled(true);
    }
    private static void death(LivingDeathEvent event) {
        if(!(event.getEntity() instanceof NpcEntity npc)||npc.level().isClientSide()||!NpcCombatRuntime.managed(npc)
                ||event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)||active(npc)||FateService.effect(npc,"phoenix")<=0)return;
        var rules=SpiritBodyRules.get().phoenix();var state=FateService.state(npc);
        var reserve=state.phoenix().initialize(rules);
        if(!reserve.ready(rules))return;
        var clock=PhoenixEmberClock.begin(npc.getMaxHealth(),reserve,rules);
        var ember=new JsonObject();ember.add("clock",GSON.toJsonTree(clock));
        ember.addProperty("damage_type",npc.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getKey(event.getSource().type()).toString());
        ember.addProperty("killer",npc.combatLastAttacker);
        if(event.getSource().getEntity()!=null)ember.addProperty("attacker",event.getSource().getEntity().getUUID().toString());
        var extras=NpcFateRuntime.extras(npc);extras.add(EMBER,ember);
        CultivationService.replaceFates(npc,state.withPhoenix(reserve.emptyReserve()),false);
        NpcFateRuntime.extras(npc,extras);
        event.setCanceled(true);npc.setHealth(HealingMath.floorFloat(clock.durability()));
        npc.stopUsingItem();npc.stopRiding();npc.getNavigation().stop();npc.setTarget(null);npc.setPose(Pose.SWIMMING);
        com.example.myvillage.combat.session.CombatSessionManager.interruptNpc(npc,com.example.myvillage.combat.session.CombatStopReason.DISALLOWED,false);
        clearInjury(npc);
    }
    public static void tick(NpcEntity npc) {
        if(!NpcCombatRuntime.managed(npc)||!NpcCombatRuntime.canTick(npc))return;
        var rules=SpiritBodyRules.get().phoenix();var clock=clock(npc);
        if(clock!=null) {
            npc.getNavigation().stop();npc.setTarget(null);npc.setPose(Pose.SWIMMING);
            npc.setDeltaMovement(0,Math.min(0,npc.getDeltaMovement().y),0);npc.fallDistance=0;clearInjury(npc);
            clock=clock.health(npc.getHealth()).tick(npc.isInWater(),rules);
            var extras=NpcFateRuntime.extras(npc);var ember=extras.getAsJsonObject(EMBER);ember.add("clock",GSON.toJsonTree(clock));
            if(clock.failed()||clock.complete()) {
                extras.remove(EMBER);
                if(clock.complete())extras.addProperty(PROTECTED,FateActors.now(npc)+rules.rebirthProtectionTicks());
                NpcFateRuntime.extras(npc,extras);npc.setPose(Pose.STANDING);
                if(clock.complete())npc.setHealth(HealingMath.floorFloat(npc.getMaxHealth()*rules.rebirthHealthFraction()));
                else {
                    var type=npc.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolder(ResourceLocation.parse(ember.get("damage_type").getAsString())).orElseThrow();
                    var attacker=ember.has("attacker")?((ServerLevel)npc.level()).getEntity(java.util.UUID.fromString(ember.get("attacker").getAsString())):null;
                    npc.combatLastAttacker=ember.get("killer").getAsString();npc.combatLastDamageTick=npc.level().getGameTime();
                    npc.setHealth(0);npc.die(new DamageSource(type,attacker));
                }
            } else {
                NpcFateRuntime.extras(npc,extras);npc.setHealth(HealingMath.floorFloat(clock.durability()));
                if(clock.remaining()%rules.particleIntervalTicks()==0)((ServerLevel)npc.level()).sendParticles(ParticleTypes.FLAME,
                        npc.getX(),npc.getY()+.15,npc.getZ(),rules.particlesPerPulse(),.18,.08,.18,.01);
            }
            return;
        }
        if(!npc.isAlive()||npc.isNoAi()||FateService.effect(npc,"phoenix")<=0)return;
        var state=FateService.state(npc);var fire=state.phoenix().initialize(rules);
        if(npc.tickCount%20==0)fire=fire.recover(rules.naturalPerSecond()+(warm(npc,rules.warmRadius())?rules.warmPerSecond():0),rules);
        if(!fire.equals(state.phoenix()))CultivationService.replaceFates(npc,state.withPhoenix(fire),false);
        if(npc.tickCount%20!=0)return;
        double budget=fire.healingBudget(rules),raw=npc.getMaxHealth()*rules.healingHealthFractionPerSeed()*budget;
        double expected=Math.min(Math.max(0,npc.getMaxHealth()-npc.getHealth()),raw*HealingRuntime.multiplier(npc));
        if(expected<=0)return;
        double actual=HealingRuntime.heal(npc,HealingMath.floorFloat(raw),HEAL);
        if(actual>0){var latest=FateService.state(npc);CultivationService.replaceFates(npc,
                latest.withPhoenix(latest.phoenix().spendHealing(Math.min(budget,budget*actual/expected),rules)),false);}
    }
    private static void clearInjury(NpcEntity npc) {
        npc.clearFire();npc.setTicksFrozen(0);npc.removeEffect(net.minecraft.world.effect.MobEffects.POISON);npc.removeEffect(net.minecraft.world.effect.MobEffects.WITHER);
    }
    private static boolean warm(NpcEntity npc,int radius) {
        var level=npc.level();var center=npc.blockPosition();
        for(var pos:BlockPos.betweenClosed(center.offset(-radius,-radius,-radius),center.offset(radius,radius,radius))) {
            if(pos.distSqr(center)>radius*radius||!level.hasChunkAt(pos))continue;
            var block=level.getBlockState(pos);
            if(!(block.is(Blocks.FIRE)||block.is(Blocks.SOUL_FIRE)||block.is(Blocks.LAVA)||block.getBlock() instanceof CampfireBlock&&block.getValue(CampfireBlock.LIT)))continue;
            var hit=level.clip(new ClipContext(npc.getEyePosition(),Vec3.atCenterOf(pos),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,npc));
            if(hit.getType()==HitResult.Type.MISS||hit.getBlockPos().equals(pos))return true;
        }
        return false;
    }
}
