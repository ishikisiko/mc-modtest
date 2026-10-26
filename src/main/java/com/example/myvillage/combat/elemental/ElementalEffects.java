package com.example.myvillage.combat.elemental;

import com.example.myvillage.combat.CombatMode;
import com.example.myvillage.combat.CombatService;
import com.example.myvillage.combat.runtime.*;
import com.example.myvillage.cultivation.CultivationService;
import com.example.myvillage.stats.*;
import com.example.myvillage.stats.runtime.StatsRuntime;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.*;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import org.joml.Vector3f;
import org.slf4j.LoggerFactory;
import java.util.*;

/** Server-owned, bounded combat-only state; no saved player profile fields and no client authority. */
public final class ElementalEffects {
    private static final ResourceLocation SPEED=ResourceLocation.parse("myvillage:elemental_flow");
    private record DotKey(UUID caster,UUID target,DamageType element) {}
    private static final class Dot {
        final ServerPlayer caster;final Entity target;final DamageType element;final double damage;final long until;long next;
        Dot(ServerPlayer p,Entity t,DamageType e,double d,long now) {
            caster=p;target=t;element=e;damage=d;
            until=now+ElementalRules.integer("dot_lifetime_ticks",1,400);next=now+interval();
        }
        double remaining(long now) {
            long first=Math.max(next,now);return first>until?0:(1+(until-first)/interval())*damage;
        }
    }
    private record Ward(ServerPlayer player,long until,double fraction,float red,float green,float blue) {}
    private static final Map<UUID,EnumMap<DamageType,ElementalCharge>> CHARGES=new HashMap<>();
    private static final Map<DotKey,Dot> DOTS=new HashMap<>();
    private static final Map<UUID,Ward> WARDS=new HashMap<>();
    private ElementalEffects() {}
    public static long clock(ServerPlayer p){return p.serverLevel().getServer().overworld().getGameTime();}
    private static int interval(){return ElementalRules.integer("dot_interval_ticks",1,100);}
    public static int charges(ServerPlayer p,DamageType type) {
        var map=CHARGES.get(p.getUUID());return map==null||!map.containsKey(type)?0:map.get(type).value(clock(p));
    }
    public static void gain(ServerPlayer p,DamageType type) {
        var map=CHARGES.computeIfAbsent(p.getUUID(),id->new EnumMap<>(DamageType.class));
        var meter=map.computeIfAbsent(type,t->new ElementalCharge());
        int max=ElementalRules.integer("charge_maximum",1,6);
        int value=meter.gain(clock(p),max,ElementalRules.integer("charge_lifetime_ticks",20,1200));
        p.displayClientMessage(Component.translatable("combat.myvillage.elemental."+type.id(),value,max),true);
        LoggerFactory.getLogger(ElementalEffects.class).info("ELEMENTAL player={} result=charge element={} value={}",p.getGameProfile().getName(),type.id(),value);
    }
    public static int consume(ServerPlayer p,DamageType type) {
        var map=CHARGES.get(p.getUUID());return map==null||!map.containsKey(type)?0:map.get(type).consume(clock(p));
    }
    /** A fresh coefficient independent of held melee damage, enchantments and melee-only buffs. */
    public static double base(ServerPlayer p,DamageType type) {
        var profile=CultivationService.getProfile(p);var rules=StatsRules.bundled();
        var key=rules.playerKey(profile.realmId().toString(),profile.stageId().toString());
        int affinity=profile.spiritualRoot().map(root->root.affinitiesBasisPoints().getOrDefault(ResourceLocation.parse("myvillage:"+type.id()),0)).orElse(0);
        double foundation=com.example.myvillage.alchemy.FoundationRules.get().bonus(profile);
        return ElementalRules.number("base_damage",.1,100)*rules.growth(key)*(1+foundation)
                *(1+affinity/10000.0*ElementalRules.number("affinity_bonus",0,1));
    }
    private static DotKey key(ServerPlayer p,Entity target,DamageType element){return new DotKey(p.getUUID(),CombatTargets.owner(target).getUUID(),element);}
    public static double harvestDamage(ServerPlayer p,Entity target,DamageType element) {
        var dot=DOTS.get(key(p,target,element));return dot==null?0:dot.remaining(clock(p));
    }
    public static void harvest(ServerPlayer p,Entity target,DamageType element){DOTS.remove(key(p,target,element));}
    public static void seed(ServerPlayer p,Entity target,DamageType element,double base) {
        var k=key(p,target,element);
        if(!DOTS.containsKey(k) && DOTS.keySet().stream().filter(d->d.caster().equals(p.getUUID())).count()
                >=ElementalRules.integer("maximum_dots_per_caster",1,48))return;
        DOTS.put(k,new Dot(p,target,element,base*ElementalRules.number("dot_multiplier",.01,2),clock(p)));
    }
    public static void ward(ServerPlayer p,ElementalSpell spell,int charge,float red,float green,float blue) {
        removeWard(p.getUUID());
        WARDS.put(p.getUUID(),new Ward(p,clock(p)+spell.wardTicks()+(long)spell.wardPerCharge()*charge,spell.wardFraction(),red,green,blue));
        if(spell.healPerCharge()>0 && charge>0) p.heal((float)(base(p,spell.element())*charge*spell.healPerCharge()));
        var speed=p.getAttribute(Attributes.MOVEMENT_SPEED);
        if(speed!=null && spell.speedBonus()>0)speed.addTransientModifier(new AttributeModifier(SPEED,spell.speedBonus(),AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }
    public static void guard(LivingIncomingDamageEvent event) {
        if(!(event.getEntity() instanceof ServerPlayer p))return;
        var ward=WARDS.get(p.getUUID());if(ward==null)return;
        if(clock(p)>=ward.until() || !active(p)){removeWard(p.getUUID());return;}
        var source=event.getSource();
        if((source.getEntity()==null && !com.example.myvillage.cultivation.tribulation.TribulationDamage.tribulation(source)) || source.is(DamageTypeTags.BYPASSES_SHIELD) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY))return;
        if(event.getAmount()>0)event.setAmount((float)(event.getAmount()*(1-ward.fraction())));
    }
    private static boolean active(ServerPlayer p) {
        return p.isAlive()&&!p.isRemoved()&&!p.isSpectator()&&CombatService.getMode(p)==CombatMode.CULTIVATION;
    }
    public static void tick(MinecraftServer server) {
        long now=server.overworld().getGameTime();
        for(var id:List.copyOf(CHARGES.keySet())) {
            var p=server.getPlayerList().getPlayer(id);
            if(p==null||!active(p))forget(id);
            else {CHARGES.get(id).values().forEach(c->c.value(now));}
        }
        for(var id:List.copyOf(WARDS.keySet())) {
            var w=WARDS.get(id);
            if(now>=w.until()||!active(w.player())){removeWard(id);continue;}
            if(now%ElementalRules.integer("ward_particle_interval_ticks",1,100)==0)
                w.player().serverLevel().sendParticles(new DustParticleOptions(new Vector3f(w.red(),w.green(),w.blue()),.8f),
                        w.player().getX(),w.player().getY()+1,w.player().getZ(),8,.45,.6,.45,0);
        }
        for(var entry:List.copyOf(DOTS.entrySet())) {
            var k=entry.getKey();var d=entry.getValue();
            if(DOTS.get(k)!=d)continue;
            var owner=CombatTargets.owner(d.target);
            if(now>d.until || !active(d.caster) || !owner.isAlive() || owner.isRemoved() || d.target.isRemoved()
                    || d.caster.level()!=d.target.level() || !CombatActors.mayHarm(d.caster,owner)
                    || d.caster.isAlliedTo(owner) || owner.isAlliedTo(d.caster)) {DOTS.remove(k);continue;}
            if(now<d.next)continue;
            d.next=now+interval(); // No catch-up burst after unloaded or stalled ticks.
            var source=ElementalRules.source(d.caster,d.element);
            CombatDamageService.hurtElementalTick(d.caster,d.target,source,StatsRuntime.outgoing(d.caster,d.damage,1));
        }
    }
    private static void removeWard(UUID id) {
        var w=WARDS.remove(id);if(w==null)return;
        var speed=w.player().getAttribute(Attributes.MOVEMENT_SPEED);if(speed!=null)speed.removeModifier(SPEED);
    }
    public static void forget(UUID id) {
        CHARGES.remove(id);removeWard(id);DOTS.keySet().removeIf(k->k.caster().equals(id)||k.target().equals(id));
    }
    public static void clear(){for(var id:List.copyOf(WARDS.keySet()))removeWard(id);CHARGES.clear();DOTS.clear();}
}
