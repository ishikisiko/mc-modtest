package com.example.myvillage.combat.elemental;

import com.example.myvillage.combat.art.ArtDefinition;
import com.example.myvillage.combat.definition.AttackMoveDefinition;
import com.example.myvillage.combat.runtime.*;
import com.example.myvillage.combat.session.CombatSession;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import org.slf4j.LoggerFactory;
import java.util.*;

/** A subordinate cast owned and advanced only by CombatSessionManager/CombatArtActions. */
public final class ElementalActions {
    public static final class Cast {
        public final Vec3 eye,anchor,direction;
        final Entity target;
        final int charges;
        final double base;
        int pulse;
        boolean charged;
        Cast(Vec3 eye,Vec3 anchor,Vec3 direction,Entity target,int charges,double base) {
            this.eye=eye;this.anchor=anchor;this.direction=direction;this.target=target;this.charges=charges;this.base=base;
        }
    }
    private ElementalActions() {}
    public static Cast begin(ServerPlayer p,CombatSession session,ElementalSpell spell) {
        Vec3 eye=p.getEyePosition(),look=p.getLookAngle();
        Entity target=spell.shape().equals("self")?null:p.serverLevel().getEntities(p,p.getBoundingBox().inflate(spell.range()),
                e->CombatHitResolver.legalTarget(p,e,session)).stream()
                .filter(e->LockedSwordTargets.within(p,e,spell.range())&&LockedSwordTargets.visible(p,e)
                        && e.getBoundingBox().getCenter().subtract(eye).normalize().dot(look)>=Math.cos(Math.toRadians(spell.arc()/2)))
                .min(Comparator.<Entity>comparingDouble(e->-e.getBoundingBox().getCenter().subtract(eye).normalize().dot(look))
                        .thenComparingDouble(e->e.distanceToSqr(p)).thenComparingInt(Entity::getId)).orElse(null);
        Vec3 endpoint=eye.add(look.scale(spell.range()));
        var hit=p.level().clip(new ClipContext(eye,endpoint,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));
        if(hit.getType()==HitResult.Type.BLOCK)endpoint=hit.getLocation();
        Vec3 anchor=spell.shape().equals("self")?p.position():target==null?endpoint:target.position();
        Vec3 direction=target==null?look:target.getBoundingBox().getCenter().subtract(eye).normalize();
        int charges=spell.consumes()?ElementalEffects.consume(p,spell.element()):0;
        double base=ElementalEffects.base(p,spell.element());
        LoggerFactory.getLogger(ElementalActions.class).info("ELEMENTAL player={} t={} result=cast element={} role={} charges={} base={}",
                p.getGameProfile().getName(),p.serverLevel().getGameTime(),spell.element().id(),spell.role(),charges,base);
        return new Cast(eye,anchor,direction,target,charges,base);
    }
    public static boolean resolve(ServerPlayer p,CombatSession session,ArtDefinition art,Cast cast,int age) {
        var spell=art.elemental().orElseThrow();if(!spell.due(age,cast.pulse))return false;
        int pulse=cast.pulse++;
        if(spell.role().equals("ward")) {
            ElementalEffects.ward(p,spell,cast.charges,art.red(),art.green(),art.blue());
            if(spell.push()>0)for(var target:LockedSwordTargets.inArray(p,session,p.position(),spell.radius(),spell.targets()))push(p,target,spell.push());
            LoggerFactory.getLogger(ElementalActions.class).info("ELEMENTAL player={} result=ward art={} charges={} reduction={}",p.getGameProfile().getName(),art.id(),cast.charges,spell.wardFraction());
            return true;
        }
        double scale=spell.role().equals("burst")?ElementalCharge.multiplier(cast.charges,ElementalRules.number("charge_bonus",0,1)):1;
        AttackMoveDefinition move=art.move(0,1);
        int successes=0;
        for(var target:targets(p,session,spell,cast)) {
            double harvest=spell.harvest()?ElementalEffects.harvestDamage(p,target,spell.element()):0;
            // Harvest is folded into this one hit, so defenses and i-frames are resolved exactly once.
            double base=cast.base*scale+harvest/Math.max(.01,move.damageMultiplier());
            var result=CombatDamageService.applyElemental(p,target,move,session,ElementalRules.source(p,spell.element()),base);
            if(!session.hasActiveAction() || !p.isAlive())break;
            if(result.successful() && result.actualDamage()>0) {
                successes++;
                if(spell.harvest())ElementalEffects.harvest(p,target,spell.element());
                if(spell.dot())ElementalEffects.seed(p,target,spell.element(),cast.base);
                var owner=CombatTargets.owner(target);
                if(owner instanceof LivingEntity living) {
                    if(spell.slowTicks()>0)living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,spell.slowTicks(),spell.slowAmplifier(),false,false,true),p);
                    if(spell.armorBreak()>0)SwordArmorBreak.apply(living,spell.armorBreak(),spell.armorTicks());
                }
                if(spell.push()>0)push(p,target,spell.push());
            }
            LoggerFactory.getLogger(ElementalActions.class).info("ELEMENTAL player={} t={} result=hit art={} pulse={} target={} element={} charges={} harvest={} success={} damage={}",
                    p.getGameProfile().getName(),p.serverLevel().getGameTime(),art.id(),pulse,target.getId(),spell.element().id(),cast.charges,harvest,result.successful(),result.actualDamage());
        }
        if(successes>0 && spell.role().equals("basic") && !cast.charged) {cast.charged=true;ElementalEffects.gain(p,spell.element());}
        LoggerFactory.getLogger(ElementalActions.class).info("ELEMENTAL player={} result=pulse art={} pulse={} hits={}",p.getGameProfile().getName(),art.id(),pulse,successes);
        return true;
    }
    private static List<Entity> targets(ServerPlayer p,CombatSession session,ElementalSpell spell,Cast cast) {
        if(spell.shape().equals("bolt"))return cast.target!=null && CombatHitResolver.legalTarget(p,cast.target,session)
                && LockedSwordTargets.within(p,cast.target,spell.range())&&LockedSwordTargets.visible(p,cast.target)
                ?List.of(cast.target):List.of();
        if(spell.shape().equals("field")||spell.shape().equals("burst")) {
            if(p.position().distanceToSqr(cast.anchor)>spell.range()*spell.range())return List.of();
            return LockedSwordTargets.inArray(p,session,cast.anchor,spell.radius(),spell.targets());
        }
        if(spell.shape().equals("cone"))return LockedSwordTargets.inArray(p,session,p.position(),spell.radius()*2,spell.targets()).stream()
                .filter(e->e.getBoundingBox().getCenter().subtract(p.getEyePosition()).normalize().dot(cast.direction)>=.5).toList();
        // Fixed initial ray, bounded by actual block collision at resolution as well as per-target LOS.
        Vec3 end=cast.eye.add(cast.direction.scale(spell.range()));
        var wall=p.level().clip(new ClipContext(cast.eye,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));
        if(wall.getType()==HitResult.Type.BLOCK)end=wall.getLocation();
        final Vec3 finalEnd=end;
        double width=ElementalRules.number("aim_width",.05,1);
        var list=p.serverLevel().getEntities(p,new AABB(cast.eye,end).inflate(width+1),e->CombatHitResolver.legalTarget(p,e,session)).stream()
                .filter(e->LockedSwordTargets.within(p,e,spell.range())&&LockedSwordTargets.visible(p,e)
                        && e.getBoundingBox().inflate(width).clip(cast.eye,finalEnd).isPresent()
                        && LockedSwordTargets.clear(p,cast.eye,e.getBoundingBox().getCenter()))
                .sorted(Comparator.comparingDouble((Entity e)->e.distanceToSqr(cast.eye)).thenComparingInt(Entity::getId)).toList();
        Set<Integer> seen=new HashSet<>();return list.stream().filter(e->seen.add(CombatTargets.hitId(e))).limit(spell.targets()).toList();
    }
    private static void push(ServerPlayer p,Entity target,double strength) {
        // Never move multipart parents, and keep the target's native knockback resistance.
        if(!(target instanceof LivingEntity living) || target instanceof net.neoforged.neoforge.entity.PartEntity<?>)return;
        if(com.example.myvillage.combat.runtime.StaggerResistant.resists(living))return;
        if(target instanceof ServerPlayer defender && CombatDodgeService.isDodging(defender))return;
        var delta=target.position().subtract(p.position());
        living.knockback(strength,-delta.x,-delta.z);
        if(target instanceof ServerPlayer defender)defender.connection.send(new ClientboundSetEntityMotionPacket(defender));
    }
}
