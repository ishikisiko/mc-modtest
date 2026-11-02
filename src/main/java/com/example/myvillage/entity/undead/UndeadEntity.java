package com.example.myvillage.entity.undead;

import com.example.myvillage.combat.elemental.ElementalRules;
import com.example.myvillage.combat.runtime.CombatActors;
import com.example.myvillage.combat.runtime.CombatReactionService;
import com.example.myvillage.entity.beast.*;
import com.example.myvillage.entity.npc.CultivatorEntity;
import com.example.myvillage.stats.runtime.StatsRuntime;
import java.util.*;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.*;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;

/** Timed attacks only: no vanilla melee/shooting goal runs alongside this executor. */
public final class UndeadEntity extends BeastEntity {
    private final List<Field> fields = new ArrayList<>();
    private final List<Wave> waves = new ArrayList<>();
    private final Set<UUID> projectiles = new HashSet<>();
    public UndeadEntity(EntityType<? extends UndeadEntity> type, Level level) {
        super(type, level);
        xpReward = rules().ticks("experience");
    }
    private static UndeadRules rules() { return UndeadRules.get(); }
    @Override protected ResourceLocation beastId() { return EntityType.getKey(getType()); }
    public UndeadRules.Variant variant() { return rules().require(beastId().getPath()); }
    @Override public boolean resistsStagger() { return false; }
    @Override protected void registerGoals() {
        super.registerGoals();
        targetSelector.addGoal(rules().ticks("cultivator_target_priority"),
                new NearestAttackableTargetGoal<>(this, CultivatorEntity.class, true,
                        target -> CombatActors.mayHarm(this, target)));
        goalSelector.addGoal(1, new ArcherStance());
    }
    @Override protected void strike(BeastMoveDefinition move, LivingEntity target) { /* event ticks below own each individual hit */ }
    @Override protected void onMoveTick(BeastMoveDefinition move, int tick) {
        var action = variant().action(move.animation());
        if (action.strikesAt(tick)) {
            switch (action.mode()) {
                case "arrow" -> shoot(action, move);
                case "wave" -> { if (waves.size() < rules().ticks("maximum_projectiles_per_shooter")) waves.add(new Wave(position().add(0, rules().number("wave_height"), 0), BeastGeometry.forward(lockedMoveYaw()).scale(rules().number("wave_speed")), move)); }
                default -> melee(action, move);
            }
        }
        if (action.mode().equals("arrow") && tick < move.windupTicks() && tick % rules().ticks("particle_interval") == 0) {
            ((ServerLevel)level()).sendParticles(ParticleTypes.CRIT, getX(), getEyeY(), getZ(), rules().ticks("telegraph_count"), rules().number("telegraph_spread"), rules().number("telegraph_spread"), rules().number("telegraph_spread"), 0);
        }
    }
    private void melee(UndeadRules.Action action, BeastMoveDefinition move) {
        var hit = move.hit(); int count = 0;
        var victims = level().getEntitiesOfClass(LivingEntity.class, BeastGeometry.hitBounds(hit, getX(), getY(), getZ(), lockedMoveYaw()), this::mayHit);
        victims.sort(Comparator.comparingDouble(this::distanceToSqr));
        for (var victim : victims) {
            if (!BeastGeometry.hitOverlaps(hit, getX(), getY(), getZ(), lockedMoveYaw(), victim.getBoundingBox()) || !hasLineOfSight(victim)) continue;
            if (action.mode().equals("cone")) {
                Vec3 delta = victim.position().subtract(position()).multiply(1,0,1).normalize();
                if (delta.dot(BeastGeometry.forward(lockedMoveYaw())) < rules().number("cone_min_dot")) continue;
            }
            float before = victim.getHealth();
            super.hitVictim(move, victim);
            if (victim.getHealth() < before) applyEffect(victim, action.effect(), before-victim.getHealth(), BeastGeometry.forward(lockedMoveYaw()));
            if (++count >= move.maximumTargets()) break;
        }
        if (action.mode().equals("cone")) particles(position().add(BeastGeometry.forward(lockedMoveYaw()).scale(hit.far()/2)), action.effect());
    }
    public boolean mayHit(LivingEntity target) { return CombatActors.mayHarm(this, target) && isVictim(target, getTarget()); }
    private void shoot(UndeadRules.Action action, BeastMoveDefinition move) {
        ServerLevel level = (ServerLevel)level();
        if (getTarget() == null || !getTarget().isAlive()) return;
        projectiles.removeIf(id -> level.getEntity(id)==null);
        int live = projectiles.size();
        int count = action.projectile().equals("spread") ? rules().ticks("spread_count") : 1;
        if (live+count > rules().ticks("maximum_projectiles_per_shooter")) return;
        Vec3 forward=BeastGeometry.forward(lockedMoveYaw());
        Vec3 left=new Vec3(forward.z,0,-forward.x);
        Vec3 start = getEyePosition().subtract(0,rules().number("muzzle_drop"),0)
                .add(forward.scale(rules().number("muzzle_forward"))).add(left.scale(rules().number("muzzle_side")));
        Vec3 chest=getEyePosition().subtract(0,rules().number("muzzle_drop"),0);
        if (!level.hasChunkAt(net.minecraft.core.BlockPos.containing(start))
                || level.clip(new ClipContext(chest,start,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this)).getType()!=HitResult.Type.MISS) return;
        boolean ground = Set.of("arc","gu","tide","explosive").contains(action.projectile());
        Vec3 aim = lockedAimPoint().add(0, rules().number(ground ? "ground_aim_height" : "body_aim_height"), 0);
        double speed = rules().number(action.projectile().equals("arc") ? "arc_speed" : "projectile_speed");
        double duration = Math.max(1, aim.subtract(start).horizontalDistance()/speed);
        Vec3 velocity = aim.subtract(start).scale(1/duration).add(0, rules().number("projectile_gravity")*duration/2, 0);
        for (int i=0; i<count; i++) {
            double degrees = count==1 ? 0 : (i-(count-1)/2.)*rules().number("spread_degrees");
            var arrow = new UndeadArrowEntity(UndeadEntities.ARROW.get(), level);
            arrow.prepare(this, action, move.damageMultiplier());
            arrow.setPos(start); arrow.setDeltaMovement(velocity.yRot((float)Math.toRadians(degrees)));
            if (level.addFreshEntity(arrow)) projectiles.add(arrow.getUUID());
        }
        playSound(SoundEvents.SKELETON_SHOOT, (float)rules().number("shoot_volume"), (float)rules().number("shoot_pitch"));
    }
    public boolean arrowHit(LivingEntity victim, UndeadArrowEntity arrow, double multiplier) {
        if (!mayHit(victim)) return false;
        return StatsRuntime.hurt(this, victim, damageSources().arrow(arrow,this), StatsRuntime.outgoing(this,getAttributeValue(Attributes.ATTACK_DAMAGE),multiplier));
    }
    public void arrowImpact(UndeadRules.Action action, Vec3 point, Set<UUID> alreadyHit) {
        String kind = action.projectile();
        if (!Set.of("tide","gu","explosive").contains(kind)) return;
        if (fields.size() >= rules().ticks("maximum_fields_per_shooter")) return;
        fields.add(new Field(point,kind,Set.copyOf(alreadyHit)));
    }
    @Override protected void customServerAiStep() {
        super.customServerAiStep();
        tickWaves(); tickFields();
    }
    private void tickWaves() {
        for (var iterator=waves.iterator(); iterator.hasNext();) {
            Wave wave=iterator.next(); Vec3 end=wave.point.add(wave.velocity);
            if (!level().hasChunkAt(net.minecraft.core.BlockPos.containing(end))) { iterator.remove(); continue; }
            var wall=level().clip(new ClipContext(wave.point,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this));
            if (wall.getType()!=HitResult.Type.MISS) end=wall.getLocation();
            double radius=rules().number("wave_radius"); int count=0;
            for (var victim:level().getEntitiesOfClass(LivingEntity.class,new AABB(wave.point,end).inflate(radius),this::mayHit)) {
                if (wave.hit.contains(victim.getUUID()) || !hasLineOfSight(victim)) continue;
                if (victim.getBoundingBox().inflate(radius).clip(wave.point,end).isEmpty() && !victim.getBoundingBox().inflate(radius).contains(wave.point)) continue;
                wave.hit.add(victim.getUUID()); float before=victim.getHealth();
                StatsRuntime.hurt(this,victim,damageSources().mobAttack(this),StatsRuntime.outgoing(this,getAttributeValue(Attributes.ATTACK_DAMAGE),wave.move.damageMultiplier()));
                if (victim.getHealth()<before) applyEffect(victim,"push",before-victim.getHealth(),wave.velocity.normalize());
                if (++count>=rules().ticks("maximum_targets")) break;
            }
            particles(end,"tide");wave.point=end;wave.age++;
            if (wall.getType()!=HitResult.Type.MISS || wave.age>=rules().ticks("wave_lifetime") || wave.hit.size()>=rules().ticks("maximum_targets")) iterator.remove();
        }
    }
    private void tickFields() {
        for (var iterator=fields.iterator();iterator.hasNext();) {
            Field f=iterator.next(); f.age++;
            boolean gu=f.kind.equals("gu"), explosive=f.kind.equals("explosive");
            int delay=explosive?rules().ticks("blast_delay"):1;
            if (f.age%rules().ticks("particle_interval")==0 || f.age==1) ring(f.point,f.kind);
            boolean pulse=gu ? f.age%rules().ticks("field_interval")==0 : f.age==delay;
            if (pulse) {
                double radius=rules().number("field_radius");int count=0;
                var victims=level().getEntitiesOfClass(LivingEntity.class,new AABB(f.point,f.point).inflate(radius),this::mayHit);
                victims.sort(Comparator.comparingDouble(e->e.position().distanceToSqr(f.point)));
                for (var victim:victims) {
                    if (f.skip.contains(victim.getUUID()) || victim.position().distanceToSqr(f.point)>radius*radius) continue;
                    if (level().clip(new ClipContext(f.point.add(0,rules().number("field_floor_offset"),0),victim.getEyePosition(),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this)).getType()!=HitResult.Type.MISS) continue;
                    float before=victim.getHealth();
                    StatsRuntime.hurt(this,victim,damageSources().mobAttack(this),StatsRuntime.outgoing(this,getAttributeValue(Attributes.ATTACK_DAMAGE),gu?rules().number("field_damage_multiplier"):1));
                    if (victim.getHealth()<before) applyEffect(victim,gu?"poison":"push",before-victim.getHealth(),victim.position().subtract(f.point).multiply(1,0,1).normalize());
                    if (++count>=rules().ticks("maximum_targets")) break;
                }
            }
            if (f.age>=(gu?rules().ticks("field_ticks"):delay)) iterator.remove();
        }
    }
    private void applyEffect(LivingEntity victim,String effect,float lost,Vec3 direction) {
        switch(effect) {
            case "grasp" -> victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,rules().ticks("grasp_ticks"),0),this);
            case "poison" -> victim.addEffect(new MobEffectInstance(MobEffects.POISON,rules().ticks("poison_ticks"),0),this);
            case "leech" -> heal((float)Math.min(rules().number("leech_cap"),lost*rules().number("leech_fraction")));
            case "push" -> {
                double scale=1-Mth.clamp(victim.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE),0,1);
                victim.setDeltaMovement(victim.getDeltaMovement().add(direction.scale(rules().number("push_strength")*scale)).add(0,rules().number("push_lift")*scale,0));
                victim.hasImpulse=true;victim.hurtMarked=true;
            }
        }
    }
    private void particles(Vec3 at,String kind) { ((ServerLevel)level()).sendParticles(kind.equals("poison")||kind.equals("gu")?ParticleTypes.SPORE_BLOSSOM_AIR:kind.equals("tide")?ParticleTypes.SPLASH:ParticleTypes.CRIT,at.x,at.y,at.z,rules().ticks("particle_count"),rules().number("particle_spread_horizontal"),rules().number("particle_spread_vertical"),rules().number("particle_spread_horizontal"),0); }
    private void ring(Vec3 at,String kind) {
        int points=rules().ticks("particle_points");double radius=rules().number("field_radius");
        for (int i=0;i<points;i++) { double angle=2*Math.PI*i/points;particles(at.add(Math.cos(angle)*radius,rules().number("field_floor_offset"),Math.sin(angle)*radius),kind); }
    }
    @Override protected SoundEvent getAmbientSound() { return variant().archer()?SoundEvents.SKELETON_AMBIENT:SoundEvents.ZOMBIE_AMBIENT; }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return variant().archer()?SoundEvents.SKELETON_HURT:SoundEvents.ZOMBIE_HURT; }
    @Override protected SoundEvent getDeathSound() { return variant().archer()?SoundEvents.SKELETON_DEATH:SoundEvents.ZOMBIE_DEATH; }
    @Override protected void playWindupSound(BeastMoveDefinition move) { playSound(variant().archer()?SoundEvents.SKELETON_STEP:SoundEvents.ZOMBIE_AMBIENT,(float)rules().number("windup_volume"),(float)rules().number("windup_pitch")); }
    private final class ArcherStance extends Goal {
        ArcherStance() { setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK)); }
        public boolean canUse() { return variant().archer()&&getTarget()!=null&&getTarget().isAlive()&&hasLineOfSight(getTarget())&&distanceToSqr(getTarget())<=Math.pow(rules().number("preferred_archer_range"),2); }
        public boolean requiresUpdateEveryTick() { return true; }
        public void tick() { getNavigation().stop(); if (!isMoveRunning()&&getTarget()!=null) { getLookControl().setLookAt(getTarget());if(tickCount%rules().ticks("decision_ticks")==0)tryStartMove(getTarget()); } }
    }
    private static final class Field {
        final Vec3 point;final String kind;final Set<UUID> skip;int age;
        Field(Vec3 point,String kind,Set<UUID> skip){this.point=point;this.kind=kind;this.skip=skip;}
    }
    private static final class Wave {
        Vec3 point;final Vec3 velocity;final BeastMoveDefinition move;final Set<UUID> hit=new HashSet<>();int age;
        Wave(Vec3 point,Vec3 velocity,BeastMoveDefinition move){this.point=point;this.velocity=velocity;this.move=move;}
    }
}
