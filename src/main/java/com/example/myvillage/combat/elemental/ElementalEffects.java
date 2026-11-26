package com.example.myvillage.combat.elemental;

import com.example.myvillage.combat.CombatMode;
import com.example.myvillage.combat.foundation.ControlService;
import com.example.myvillage.combat.foundation.ControlState;
import com.example.myvillage.combat.CombatService;
import com.example.myvillage.combat.runtime.*;
import com.example.myvillage.cultivation.CultivationService;
import com.example.myvillage.stats.*;
import com.example.myvillage.stats.runtime.StatsRuntime;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import com.example.myvillage.combat.runtime.CombatCaster;
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
    private static final ResourceLocation SPEED=ResourceLocation.parse("yuanxian:elemental_flow");
    private static final ResourceLocation WATER_CHILL = ResourceLocation.parse("yuanxian:elemental_water_chill");
    private record DotKey(UUID caster,UUID target,DamageType element) {}
    private static final class Dot {
        final LivingEntity caster;final Entity target;final DamageType element;final double damage;final long until;long next;
        Dot(LivingEntity p,Entity t,DamageType e,double d,long now) {
            this(p, t, e, d, now + ElementalRules.integer("dot_lifetime_ticks", 1, 400), now + interval());
        }
        Dot(LivingEntity p, Entity t, DamageType e, double d, long until, long next) {
            caster = p; target = t; element = e; damage = d;
            this.until = until; this.next = next;
        }
        double remaining(long now) {
            long first=Math.max(next,now);return first>until?0:(1+(until-first)/interval())*damage;
        }
    }
    private record Ward(LivingEntity player, DamageType element, long until, int duration,
                        double fraction,float red,float green,float blue) {}
    private static final Map<UUID,EnumMap<DamageType,ElementalCharge>> CHARGES=new HashMap<>();
    private static final Map<DotKey,Dot> DOTS=new HashMap<>();
    private static final Map<UUID,Ward> WARDS=new HashMap<>();
    private ElementalEffects() {}
    public static long clock(LivingEntity p){return CombatCaster.level(p).getServer().overworld().getGameTime();}
    private static int interval(){return ElementalRules.integer("dot_interval_ticks",1,100);}
    public static int charges(LivingEntity p,DamageType type) {
        var map=CHARGES.get(p.getUUID());return map==null||!map.containsKey(type)?0:map.get(type).value(clock(p));
    }
    public static void gain(LivingEntity p,DamageType type) {
        var map=CHARGES.computeIfAbsent(p.getUUID(),id->new EnumMap<>(DamageType.class));
        var meter=map.computeIfAbsent(type,t->new ElementalCharge());
        int max=ElementalRules.integer("charge_maximum",1,6);
        int value=meter.gain(clock(p),max,ElementalRules.integer("charge_lifetime_ticks",20,1200));
        CombatCaster.message(p,Component.translatable("combat.yuanxian.elemental."+type.id(),value,max),true);
        LoggerFactory.getLogger(ElementalEffects.class).info("ELEMENTAL player={} result=charge element={} value={}",CombatCaster.name(p),type.id(),value);
    }
    public static int consume(LivingEntity p,DamageType type) {
        var map=CHARGES.get(p.getUUID());return map==null||!map.containsKey(type)?0:map.get(type).consume(clock(p));
    }
    public static boolean consumeOne(LivingEntity actor, DamageType type) {
        var map = CHARGES.get(actor.getUUID());
        var charge = map == null ? null : map.get(type);
        return charge != null && charge.consumeOne(clock(actor));
    }
    /** A fresh coefficient independent of held melee damage, enchantments and melee-only buffs. */
    public static double base(LivingEntity p,DamageType type) {
        var profile=CombatCaster.profile(p);
        int affinity=profile.spiritualRoot().map(root->root.points().getOrDefault(ResourceLocation.parse("yuanxian:"+type.id()),0)).orElse(0);
        var numerical = com.example.myvillage.stats.CultivationStatsRules.bundled();
        double attackAddition = com.example.myvillage.cultivation.fate.FateActors.body(p).orElseThrow().attack() - numerical.reference().attack();
        return (ElementalRules.number("base_damage",.1,100) + attackAddition)
                *com.example.myvillage.aptitude.AptitudeRules.get().multiplier(affinity)
                *(1+com.example.myvillage.cultivation.fate.FateEffects.elementBonus(type.id(),false,false,
                        effect->com.example.myvillage.cultivation.fate.FateService.effect(p,effect)));
    }
    private static DotKey key(LivingEntity p,Entity target,DamageType element){return new DotKey(p.getUUID(),CombatTargets.owner(target).getUUID(),element);}
    public static double harvestDamage(LivingEntity p,Entity target,DamageType element) {
        var dot=DOTS.get(key(p,target,element));return dot==null?0:dot.remaining(clock(p));
    }

    /** Reserves a specific scheduled pulse for a hit or conversion. Commit after the hit is accepted. */
    public static final class Pulse {
        private final DotKey key;
        private final Dot dot;
        private final long next;

        private Pulse(DotKey key, Dot dot) {
            this.key = key;
            this.dot = dot;
            this.next = dot.next;
        }

        public double damage() { return dot.damage; }

        public boolean spend() {
            long now = clock(dot.caster);
            if (DOTS.get(key) != dot || dot.next != next || dot.remaining(now) <= 0) return false;
            dot.next = Math.max(now, next) + interval();
            return true;
        }
    }

    public static Pulse pendingPulse(LivingEntity caster, Entity target, DamageType type) {
        var key = key(caster, target, type);
        var dot = DOTS.get(key);
        return dot == null || dot.remaining(clock(caster)) <= 0 ? null : new Pulse(key, dot);
    }
    public static void harvest(LivingEntity p,Entity target,DamageType element){DOTS.remove(key(p,target,element));}
    public static void seed(LivingEntity p,Entity target,DamageType element,double base) {
        if (CombatTargets.owner(target) instanceof LivingEntity living && com.example.myvillage.cultivation.fate.FateDefenses.immune(living)) return;
        var k=key(p,target,element);
        if(!DOTS.containsKey(k) && DOTS.keySet().stream().filter(d->d.caster().equals(p.getUUID())).count()
                >=ElementalRules.integer("maximum_dots_per_caster",1,48))return;
        DOTS.put(k,new Dot(p,target,element,base*ElementalRules.number("dot_multiplier",.01,2),clock(p)));
    }
    /** Transfer only future pulses with the original expiration and schedule. */
    public static int transferWood(LivingEntity caster, Entity from, List<Entity> nearby, int maximum) {
        DotKey root = key(caster, from, DamageType.WOOD);
        Dot original = DOTS.get(root);
        if (original == null || original.remaining(clock(caster)) <= 0) return 0;
        int capacity = ElementalRules.integer("maximum_dots_per_caster", 1, 48);
        long active = DOTS.keySet().stream().filter(k -> k.caster().equals(caster.getUUID())).count();
        int limit = Math.min(maximum, capacity - (int) active + 1);
        if (limit <= 0) return 0;
        Map<DotKey, Entity> recipients = new LinkedHashMap<>();
        for (Entity other : nearby) {
            if (CombatTargets.owner(other) instanceof LivingEntity living && com.example.myvillage.cultivation.fate.FateDefenses.immune(living)) continue;
            DotKey candidate = key(caster, other, DamageType.WOOD);
            if (!DOTS.containsKey(candidate)) recipients.putIfAbsent(candidate, other);
            if (recipients.size() >= limit) break;
        }
        if (recipients.isEmpty()) return 0;
        DOTS.remove(root);
        double share = original.damage / recipients.size();
        recipients.forEach((id, entity) -> DOTS.put(id,
                new Dot(caster, entity, DamageType.WOOD, share, original.until, original.next)));
        return recipients.size();
    }

    public static boolean waterChill(LivingEntity caster, LivingEntity target, int ticks, double fraction) {
        return ControlService.slow(caster, target, WATER_CHILL, ticks, fraction);
    }

    public static boolean hasWaterChill(LivingEntity caster, LivingEntity target) {
        return ControlService.snapshot(target).sources().stream().anyMatch(source ->
                source.kind() == ControlState.Kind.SLOW && source.caster().equals(caster.getUUID())
                        && source.effect().equals(WATER_CHILL.toString()));
    }

    public static boolean consumeWaterChill(LivingEntity caster, LivingEntity target) {
        return ControlService.revoke(caster, target, WATER_CHILL) > 0;
    }

    public static void ward(LivingEntity p,ElementalSpell spell,int charge,float red,float green,float blue) {
        removeWard(p.getUUID());
        int duration = spell.wardTicks() + spell.wardPerCharge() * charge;
        WARDS.put(p.getUUID(),new Ward(p,spell.element(),clock(p)+duration,duration,spell.wardFraction(),red,green,blue));
        if(spell.healPerCharge()>0 && charge>0) p.heal((float)(base(p,spell.element())*charge*spell.healPerCharge()));
        var speed=p.getAttribute(Attributes.MOVEMENT_SPEED);
        if(speed!=null && spell.speedBonus()>0)speed.addTransientModifier(new AttributeModifier(SPEED,spell.speedBonus(),AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }
    public static double wardRemaining(LivingEntity actor, DamageType element) {
        Ward ward = WARDS.get(actor.getUUID());
        if (ward == null || ward.player() != actor || ward.element() != element || !active(actor)) return 0;
        return Math.clamp((ward.until() - clock(actor)) / (double) ward.duration(), 0, 1);
    }

    public static boolean consumeWard(LivingEntity actor, DamageType element) {
        if (wardRemaining(actor, element) <= 0) return false;
        removeWard(actor.getUUID());
        return true;
    }
    public static void guard(LivingIncomingDamageEvent event) {
        LivingEntity p=event.getEntity();
        var ward=WARDS.get(p.getUUID());if(ward==null)return;
        if(clock(p)>=ward.until() || !active(p)){removeWard(p.getUUID());return;}
        var source=event.getSource();
        if((source.getEntity()==null && !com.example.myvillage.cultivation.tribulation.TribulationDamage.tribulation(source)) || source.is(DamageTypeTags.BYPASSES_SHIELD) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY))return;
        if(event.getAmount()>0)event.setAmount((float)(event.getAmount()*(1-ward.fraction())));
    }
    private static boolean active(LivingEntity p) {
        return CombatCaster.active(p);
    }
    public static void tick(MinecraftServer server) {
        long now=server.overworld().getGameTime();
        for(var id:List.copyOf(CHARGES.keySet())) {
            var p=com.example.myvillage.cultivation.fate.FateActors.find(server,id);
            if(p==null||!active(p))forget(id);
            else {CHARGES.get(id).values().forEach(c->c.value(now));}
        }
        for(var id:List.copyOf(WARDS.keySet())) {
            var w=WARDS.get(id);
            if(now>=w.until()||!active(w.player())){removeWard(id);continue;}
            if(now%ElementalRules.integer("ward_particle_interval_ticks",1,100)==0)
                CombatCaster.level(w.player()).sendParticles(new DustParticleOptions(new Vector3f(w.red(),w.green(),w.blue()),.8f),
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
