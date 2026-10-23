package com.example.myvillage.entity.beast;

import com.example.myvillage.wilderness.ExpeditionRules;
import com.example.myvillage.combat.runtime.CombatActors;
import com.example.myvillage.combat.runtime.CombatReactionService;
import com.example.myvillage.combat.runtime.CombatStepService;
import com.example.myvillage.combat.elemental.ElementalRules;
import com.example.myvillage.stats.DamageType;
import com.example.myvillage.stats.runtime.StatsRuntime;
import java.util.*;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

/** Bounded ordinary attacks. All damage still enters StatsRuntime and vanilla hurt once. */
public abstract class RosterBeastEntity extends BeastEntity {
    private Vec3 anchor;
    private long lastSeen,nextRetreat;
    private final List<Bolt> bolts=new ArrayList<>();
    private final List<Field> fields=new ArrayList<>();
    protected RosterBeastEntity(EntityType<? extends BeastEntity> type,Level level){super(type,level);xpReward=ExpeditionRules.get().mob(beastId().getPath()).get("experience").getAsInt();}
    private ExpeditionRules rules(){return ExpeditionRules.get();}
    private boolean caster(){return beastId().getPath().equals("ash_talismanist");}
    private boolean bird(){return beastId().getPath().equals("frostplume_pheasant");}
    private boolean fungus(){return beastId().getPath().equals("lantern_sporeling");}
    private boolean lizard(){return beastId().getPath().equals("redplate_lizard");}
    @Override protected void registerGoals(){super.registerGoals();goalSelector.addGoal(1,new SpacingGoal());}
    @Override protected boolean isVictim(LivingEntity e,LivingEntity target){return super.isVictim(e,target)&&CombatActors.mayHarm(this,e)&&hasLineOfSight(e);}
    @Override protected void customServerAiStep(){
        long now=level().getGameTime();var t=getTarget();
        if(t!=null){if(anchor==null){anchor=position();lastSeen=now;}if(hasLineOfSight(t))lastSeen=now;
            if(!t.isAlive()||!CombatActors.mayHarm(this,t)||position().distanceToSqr(anchor)>Math.pow(rules().number("pursuit_radius"),2)||now-lastSeen>rules().ticks("lost_target_ticks")){setTarget(null);getNavigation().stop();cancelCurrentMove(definition().chase().cancelledCooldownTicks());anchor=null;}}
        else anchor=null;
        super.customServerAiStep();
        tickBolts();tickFields();
    }
    @Override protected void onMoveTick(BeastMoveDefinition move,int tick){
        String mode=rules().mode(beastId().getPath(),move.animation());
        int active=move.activeTicks().first();
        if(mode.equals("field")&&tick>=move.turnLockTick()&&tick<active&&tick%4==0)ring(lockedAimPoint(),rules().number("field_radius"),false);
        if(tick!=active)return;
        if(mode.equals("bolt")&&bolts.size()<3){
            Vec3 start=getEyePosition();Vec3 aim=lockedAimPoint().add(0,.8,0);
            bolts.add(new Bolt(start,aim.subtract(start).normalize().scale(rules().number("projectile_speed")),move.damageMultiplier()));
            playSound(bird()?SoundEvents.SNOWBALL_THROW:SoundEvents.EVOKER_CAST_SPELL,.7F,1.1F);
        }else if(mode.equals("field")&&fields.size()<2){fields.add(new Field(lockedAimPoint(),move.damageMultiplier()));}
    }
    @Override protected void strike(BeastMoveDefinition move,LivingEntity target){if(rules().mode(beastId().getPath(),move.animation()).equals("melee"))super.strike(move,target);}
    private void tickBolts(){
        if(!(level() instanceof ServerLevel level))return;
        for(var it=bolts.iterator();it.hasNext();){var b=it.next();Vec3 end=b.pos.add(b.velocity);
            var wall=level.clip(new ClipContext(b.pos,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this));if(wall.getType()!=HitResult.Type.MISS)end=wall.getLocation();
            final Vec3 to=end;double radius=rules().number("projectile_radius");
            LivingEntity hit=null;double nearest=Double.MAX_VALUE;
            for(var e:level.getEntitiesOfClass(LivingEntity.class,new AABB(b.pos,end).inflate(radius),e->e!=this&&CombatActors.mayHarm(this,e)&&isVictim(e,getTarget()))){
                var cross=e.getBoundingBox().inflate(radius).clip(b.pos,end);double distance=cross.map(p->p.distanceToSqr(b.pos)).orElse(e.getBoundingBox().contains(b.pos)?0.:Double.MAX_VALUE);
                if(distance<nearest){nearest=distance;hit=e;}}
            for(int i=0;i<3;i++){var p=b.pos.lerp(to,i/2.);level.sendParticles(bird()?ParticleTypes.SNOWFLAKE:ParticleTypes.ENCHANT,p.x,p.y,p.z,2,.025,.025,.025,0);}
            if(hit!=null)hurtSpell(hit,b.damage,false);
            b.pos=end;b.age++;
            if(hit!=null||wall.getType()!=HitResult.Type.MISS||b.age>=rules().ticks("projectile_lifetime"))it.remove();
        }
    }
    private void tickFields(){
        if(!(level() instanceof ServerLevel level))return;
        for(var it=fields.iterator();it.hasNext();){var f=it.next();f.age++;if(f.age%4==0)ring(f.pos,rules().number("field_radius"),true);
            if(f.age%rules().ticks("field_interval")==0){double r=rules().number("field_radius");int n=0;
                for(var e:level.getEntitiesOfClass(LivingEntity.class,new AABB(f.pos.x-r,f.pos.y-.4,f.pos.z-r,f.pos.x+r,f.pos.y+2.2,f.pos.z+r),e->e!=this&&isVictim(e,getTarget()))){
                    if(e.position().multiply(1,0,1).distanceToSqr(f.pos.multiply(1,0,1))>r*r)continue;
                    if(level.clip(new ClipContext(f.pos.add(0,.2,0),e.getEyePosition(),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this)).getType()!=HitResult.Type.MISS)continue;
                    hurtSpell(e,f.damage,true);if(++n>=rules().ticks("maximum_targets"))break;
                }}
            if(f.age>=rules().ticks("field_ticks"))it.remove();
        }
    }
    private void ring(Vec3 at,double radius,boolean active){if(!(level() instanceof ServerLevel level))return;for(int i=0;i<16;i++){double a=i*Math.PI/8;level.sendParticles(active?(fungus()?ParticleTypes.SPORE_BLOSSOM_AIR:ParticleTypes.FLAME):ParticleTypes.END_ROD,at.x+Math.cos(a)*radius,at.y+.12,at.z+Math.sin(a)*radius,1,0,0,0,0);}}
    private void hurtSpell(LivingEntity target,double multiplier,boolean field){
        DamageType type=fungus()?DamageType.WOOD:bird()?DamageType.WATER:DamageType.FIRE;
        float before=target.getHealth();
        boolean hit=StatsRuntime.hurt(this,target,ElementalRules.source(this,type),StatsRuntime.outgoing(this,getAttributeValue(Attributes.ATTACK_DAMAGE),multiplier));
        if(hit&&target.getHealth()<before){
            if(bird())target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,rules().ticks("slow_ticks"),0),this);
            if(fungus())target.addEffect(new MobEffectInstance(MobEffects.POISON,rules().ticks("spore_ticks"),0),this);
        }
    }
    @Override public boolean hurt(DamageSource source,float amount){
        if(lizard()&&isMoveRunning()&&source.getEntity() instanceof LivingEntity attacker&&com.example.myvillage.wilderness.ExpeditionCombat.front(this,attacker)&&!source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_ARMOR))amount*=1-rules().number("guard_fraction");
        return super.hurt(source,amount);
    }
    @Override protected SoundEvent getHurtSound(DamageSource source){return fungus()?SoundEvents.WOOD_BREAK:bird()?SoundEvents.PARROT_HURT:caster()?SoundEvents.EVOKER_HURT:SoundEvents.FOX_HURT;}
    @Override protected SoundEvent getDeathSound(){return fungus()?SoundEvents.WOOD_BREAK:bird()?SoundEvents.PARROT_DEATH:caster()?SoundEvents.EVOKER_DEATH:SoundEvents.FOX_DEATH;}
    @Override protected SoundEvent getAmbientSound(){return fungus()?SoundEvents.AZALEA_LEAVES_STEP:bird()?SoundEvents.PARROT_AMBIENT:caster()?SoundEvents.EVOKER_AMBIENT:SoundEvents.FOX_AMBIENT;}
    @Override protected void playWindupSound(BeastMoveDefinition move){playSound(caster()?SoundEvents.EVOKER_PREPARE_ATTACK:fungus()?SoundEvents.SPORE_BLOSSOM_BREAK:bird()?SoundEvents.PARROT_FLY:SoundEvents.FOX_AGGRO,.65F,.85F);}
    private final class SpacingGoal extends Goal {
        SpacingGoal(){setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK,Flag.JUMP));}
        public boolean canUse(){return (caster()||bird())&&getTarget()!=null&&!isMoveRunning();}
        public boolean requiresUpdateEveryTick(){return true;}
        public void tick(){var t=getTarget();if(t==null||CombatReactionService.isStaggered(RosterBeastEntity.this))return;
            long now=level().getGameTime();if(now%rules().ticks("decision_ticks")!=0)return;
            getLookControl().setLookAt(t,30,30);
            if(distanceToSqr(t)<9&&now>=nextRetreat){Vec3 away=position().subtract(t.position()).multiply(1,0,1).normalize();double d=CombatStepService.safeDistance(RosterBeastEntity.this,away,rules().number("retreat_distance"),1);
                if(d>0){Vec3 p=position().add(away.scale(d));getNavigation().moveTo(p.x,p.y,p.z,1.1);if(bird()&&onGround()){setDeltaMovement(away.scale(.2).add(0,.18,0));hasImpulse=true;}}
                nextRetreat=now+rules().ticks("retreat_interval");
            }else if(!tryStartMove(t)&&distanceToSqr(t)>81)getNavigation().moveTo(t,1.0);
        }
    }
    private static final class Bolt{Vec3 pos;final Vec3 velocity;final double damage;int age;Bolt(Vec3 p,Vec3 v,double d){pos=p;velocity=v;damage=d;}}
    private static final class Field{final Vec3 pos;final double damage;int age;Field(Vec3 p,double d){pos=p;damage=d;}}
}
