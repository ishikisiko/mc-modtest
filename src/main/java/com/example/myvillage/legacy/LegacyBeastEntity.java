package com.example.myvillage.legacy;

import com.example.myvillage.entity.beast.*;
import com.example.myvillage.combat.runtime.*;
import com.example.myvillage.combat.elemental.ElementalRules;
import com.example.myvillage.stats.DamageType;
import com.example.myvillage.stats.runtime.StatsRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.sounds.*;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;
import java.util.*;

/** Four distinct behaviors built on the shared beast timeline and damage authority. */
public final class LegacyBeastEntity extends BeastEntity {
    private static final EntityDataAccessor<Integer> HEAT=SynchedEntityData.defineId(LegacyBeastEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DORMANT=SynchedEntityData.defineId(LegacyBeastEntity.class,EntityDataSerializers.BOOLEAN);
    private final List<Wave> waves=new ArrayList<>();
    private BlockPos anchor;
    private Vec3 echoPoint;
    private long echoObserved,restUntil;
    public LegacyBeastEntity(EntityType<? extends LegacyBeastEntity> type,Level level) {super(type,level);xpReward=variant().data().get("experience").getAsInt();}
    @Override protected ResourceLocation beastId() {return EntityType.getKey(getType());}
    public LegacyRules.Beast variant() {return rules().beast(beastId().getPath());}
    private static LegacyRules rules() {return LegacyRules.get();}
    public boolean sword() {return beastId().getPath().equals("wandering_sword");}
    public boolean mirror() {return beastId().getPath().equals("mirror_wraith");}
    public boolean toad() {return beastId().getPath().equals("ember_toad");}
    public boolean dormant() {return entityData.get(DORMANT);}
    public int heat() {return entityData.get(HEAT);}
    public void bind(BlockPos pos,boolean sleeping) {anchor=pos.immutable();entityData.set(DORMANT,sleeping);setPersistenceRequired();}
    @Override protected void defineSynchedData(SynchedEntityData.Builder b) {super.defineSynchedData(b);b.define(HEAT,0);b.define(DORMANT,false);}
    @Override protected void registerGoals() {super.registerGoals();goalSelector.addGoal(1,new SwordOrbit());}
    @Override protected boolean isVictim(LivingEntity e,LivingEntity target) {return super.isVictim(e,target)&&CombatActors.mayHarm(this,e)&&hasLineOfSight(e);}
    @Override public boolean tryStartMove(LivingEntity target) {return !dormant()&&level().getGameTime()>=restUntil&&super.tryStartMove(target);}
    @Override protected boolean moveEligible(BeastMoveDefinition move,LivingEntity target) {
        if(!mirror())return true;
        boolean echo=variant().mode(move.animation()).equals("echo");
        if(echoPoint==null)return !echo;
        return echo&&level().getGameTime()-echoObserved>=rules().ticks("mirror_delay_ticks");
    }
    @Override protected void customServerAiStep() {
        long now=level().getGameTime();
        if(mirror()&&echoPoint!=null&&!isMoveRunning()&&now-echoObserved>=rules().ticks("mirror_memory_ticks"))echoPoint=null;
        if(dormant()) {
            getNavigation().stop();setTarget(null);
            if(now%rules().ticks("decision_ticks")==0) {
                var player=level().getNearestPlayer(this,rules().number("anchor_activation_range"));
                if(player!=null&&CombatActors.mayHarm(this,player)&&hasLineOfSight(player)) {entityData.set(DORMANT,false);setTarget(player);playSound(SoundEvents.TRIDENT_RETURN,1,.8F);}
            }
            return;
        }
        if(anchor!=null&&distanceToSqr(anchor.getCenter())>Math.pow(rules().number("anchor_pursuit_range"),2)) {
            setTarget(null);cancelCurrentMove(definition().chase().cancelledCooldownTicks());getNavigation().moveTo(anchor.getX()+.5,anchor.getY()+1,anchor.getZ()+2.5,1);
        }
        super.customServerAiStep();
        if(toad()&&heat()<rules().ticks("toad_max_heat")&&now%rules().ticks("toad_scan_ticks")==0)consumeNearbyFire();
        tickWaves();
    }
    @Override public boolean hurt(DamageSource source,float amount) {
        if(toad()&&source.is(DamageTypeTags.IS_FIRE)&&amount>0) {
            if(!level().isClientSide) {
                entityData.set(HEAT,Math.min(rules().ticks("toad_max_heat"),heat()+1));clearFire();
                if(source.getEntity() instanceof LivingEntity attacker&&CombatActors.mayHarm(this,attacker))setTarget(attacker);
                LegacyRuntime.particles((ServerLevel)level(),getEyePosition(),ParticleTypes.FLAME,8,.25);
            }
            return false;
        }
        if(!level().isClientSide&&dormant())entityData.set(DORMANT,false);
        return super.hurt(source,amount);
    }
    private void consumeNearbyFire() {
        int r=(int)rules().number("toad_fire_radius");
        for(BlockPos p:BlockPos.betweenClosed(blockPosition().offset(-r,-1,-r),blockPosition().offset(r,2,r))) {
            if(!level().hasChunkAt(p)||!(level().getBlockState(p).getBlock() instanceof BaseFireBlock)||!LegacyRuntime.clear(this,getEyePosition(),p.getCenter()))continue;
            level().setBlock(p,Blocks.AIR.defaultBlockState(),Block.UPDATE_ALL);entityData.set(HEAT,heat()+1);
            LegacyRuntime.particles((ServerLevel)level(),p.getCenter(),ParticleTypes.FLAME,8,.15);break;
        }
    }
    @Override protected void strike(BeastMoveDefinition move,LivingEntity target) {if(variant().mode(move.animation()).equals("melee"))super.strike(move,target);}
    @Override protected void onMoveTick(BeastMoveDefinition move,int tick) {
        String mode=variant().mode(move.animation());var level=(ServerLevel)level();
        if(tick<move.activeTicks().first()&&tick%rules().ticks("particle_interval")==0) {
            if(mode.equals("echo"))LegacyRuntime.ring(level,echoPoint==null?lockedAimPoint():echoPoint,rules().number("mirror_echo_radius"),ParticleTypes.SOUL);
            if(mode.equals("quake"))LegacyRuntime.ring(level,position(),1.5,ParticleTypes.END_ROD);
            if(mode.equals("fire"))LegacyRuntime.particles(level,getEyePosition(),ParticleTypes.FLAME,3+heat()*3,.2);
        }
        if(tick!=move.activeTicks().first())return;
        switch(mode) {
            case "echo" -> {
                Vec3 point=echoPoint==null?lockedAimPoint():echoPoint;
                if(point.distanceToSqr(position())<=move.useRange().maximum()*move.useRange().maximum()&&LegacyRuntime.clear(this,getEyePosition(),point.add(0,1,0)))
                    for(var target:LegacyRuntime.targets(this,point,rules().number("mirror_echo_radius")))hurtSpecial(target,DamageType.WATER,move.damageMultiplier());
                if(debugLog())org.slf4j.LoggerFactory.getLogger(LegacyBeastEntity.class).info("LEGACY_ECHO resolve entity={} position={} delay={}",getId(),point,level.getGameTime()-echoObserved);
                LegacyRuntime.ring(level,point,rules().number("mirror_echo_radius"),ParticleTypes.SOUL);echoPoint=null;
            }
            case "fire" -> spit(move);
            case "quake" -> {if(waves.size()<rules().ticks("max_mob_effects"))waves.add(new Wave(position(),move));playSound(SoundEvents.IRON_GOLEM_ATTACK,1,.6F);}
            default -> {}
        }
    }
    private void spit(BeastMoveDefinition move) {
        int charge=heat();entityData.set(HEAT,0);restUntil=level().getGameTime()+move.totalTicks()-move.activeTicks().first()+charge*rules().ticks("toad_recovery_per_heat");
        Vec3 direction=BeastGeometry.forward(lockedMoveYaw()),from=getEyePosition(),end=from.add(direction.scale(move.useRange().maximum()));
        var wall=level().clip(new ClipContext(from,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this));if(wall.getType()==HitResult.Type.BLOCK)end=wall.getLocation();
        for(var target:LegacyRuntime.targets(this,position(),move.useRange().maximum())) {
            if(target.getBoundingBox().getCenter().subtract(from).normalize().dot(direction)>rules().number("toad_cone_dot"))hurtSpecial(target,DamageType.FIRE,move.damageMultiplier()*(1+rules().number("toad_heat_damage_multiplier")*charge));
        }
        var level=(ServerLevel)level();
        for(int i=0;i<20;i++)LegacyRuntime.particles(level,from.lerp(end,i/19.),ParticleTypes.FLAME,3+charge,.1+charge*.05);
        Vec3 at=lockedAimPoint();
        if(at.distanceToSqr(position())>move.useRange().maximum()*move.useRange().maximum()||!level.hasChunkAt(BlockPos.containing(at)))at=position().add(direction.scale(3));
        var ground=level.clip(new ClipContext(at.add(0,2,0),at.add(0,-3,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this));
        if(ground.getType()==HitResult.Type.BLOCK)at=ground.getLocation().add(0,.05,0);else at=position().add(direction.scale(2));
        var live=level.getEntitiesOfClass(LegacyProjectionEntity.class,getBoundingBox().inflate(20),e->e.kind()==LegacyProjectionEntity.EMBER&&e.ownerId()==getId());
        if(live.size()<rules().ticks("max_mob_effects")) {
            var ember=new LegacyProjectionEntity(LegacyContent.PROJECTION.get(),level);ember.setPos(at);
            ember.initialize(LegacyProjectionEntity.EMBER,this,new ItemStack(LegacyContent.item("ember_cinnabar")),rules().ticks("toad_ember_ticks"));level.addFreshEntity(ember);
        }
        playSound(SoundEvents.FIRECHARGE_USE,1,.65F);
    }
    private void tickWaves() {
        for(var it=waves.iterator();it.hasNext();) {
            var wave=it.next();wave.radius+=rules().number("quake_speed");
            if(tickCount%rules().ticks("particle_interval")==0)LegacyRuntime.ring((ServerLevel)level(),wave.origin,wave.radius,ParticleTypes.CRIT);
            for(var target:LegacyRuntime.targets(this,wave.origin,wave.radius+rules().number("quake_width"))) {
                var owner=CombatTargets.owner(target);
                if(!(owner instanceof LivingEntity living)||!living.onGround()||wave.hit.contains(owner.getUUID()))continue;
                double d=target.position().subtract(wave.origin).horizontalDistance();
                if(Math.abs(d-wave.radius)>rules().number("quake_width"))continue;
                wave.hit.add(owner.getUUID());hurtSpecial(target,DamageType.EARTH,wave.move.damageMultiplier());
            }
            if(wave.radius>=wave.move.useRange().maximum())it.remove();
        }
    }
    private void hurtSpecial(Entity target,DamageType type,double multiplier) {
        if(!LegacyRuntime.legal(this,target)||!hasLineOfSight(target))return;
        StatsRuntime.hurt(this,target,ElementalRules.source(this,type),StatsRuntime.outgoing(this,getAttributeValue(Attributes.ATTACK_DAMAGE),multiplier));
    }
    public static void observeAttack(ServerPlayer player) {
        for(var e:player.serverLevel().getEntitiesOfClass(LegacyBeastEntity.class,player.getBoundingBox().inflate(rules().number("mirror_observation_range")),e->e.mirror()&&e.getTarget()==player&&e.echoPoint==null&&e.hasLineOfSight(player))) {
            e.echoPoint=player.position();e.echoObserved=player.level().getGameTime();
            if(debugLog())org.slf4j.LoggerFactory.getLogger(LegacyBeastEntity.class).info("LEGACY_ECHO observe entity={} player={} position={}",e.getId(),player.getGameProfile().getName(),e.echoPoint);
        }
    }
    @Override public void addAdditionalSaveData(CompoundTag tag) {super.addAdditionalSaveData(tag);tag.putInt("LegacyHeat",heat());tag.putBoolean("LegacyDormant",dormant());if(anchor!=null)tag.putLong("LegacyAnchor",anchor.asLong());}
    @Override public void readAdditionalSaveData(CompoundTag tag) {super.readAdditionalSaveData(tag);entityData.set(HEAT,Math.clamp(tag.getInt("LegacyHeat"),0,rules().ticks("toad_max_heat")));entityData.set(DORMANT,tag.getBoolean("LegacyDormant"));anchor=tag.contains("LegacyAnchor")?BlockPos.of(tag.getLong("LegacyAnchor")):null;waves.clear();echoPoint=null;restUntil=0;}
    @Override public void remove(RemovalReason reason) {
        if(reason==RemovalReason.KILLED&&!level().isClientSide&&anchor!=null&&level().hasChunkAt(anchor)&&level().getBlockEntity(anchor) instanceof LegacySteleEntity stele)stele.defeated(getUUID());
        super.remove(reason);
    }
    @Override protected SoundEvent getAmbientSound() {return sword()?SoundEvents.AMETHYST_BLOCK_CHIME:mirror()?SoundEvents.ALLAY_AMBIENT_WITHOUT_ITEM:toad()?SoundEvents.FROG_AMBIENT:SoundEvents.STONE_STEP;}
    @Override protected SoundEvent getHurtSound(DamageSource source) {return sword()?SoundEvents.ANVIL_HIT:mirror()?SoundEvents.ALLAY_HURT:toad()?SoundEvents.FROG_HURT:SoundEvents.IRON_GOLEM_HURT;}
    @Override protected SoundEvent getDeathSound() {return sword()?SoundEvents.TRIDENT_HIT_GROUND:mirror()?SoundEvents.ALLAY_DEATH:toad()?SoundEvents.FROG_DEATH:SoundEvents.IRON_GOLEM_DEATH;}
    @Override protected void playWindupSound(BeastMoveDefinition move) {playSound(toad()?SoundEvents.FROG_TONGUE:mirror()?SoundEvents.EVOKER_PREPARE_ATTACK:sword()?SoundEvents.TRIDENT_THROW.value():SoundEvents.IRON_GOLEM_ATTACK,.8F,.8F);}
    private static final class Wave {final Vec3 origin;final BeastMoveDefinition move;final Set<UUID> hit=new HashSet<>();double radius;Wave(Vec3 o,BeastMoveDefinition m){origin=o;move=m;}}
    private final class SwordOrbit extends Goal {
        SwordOrbit(){setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));}
        @Override public boolean canUse(){return sword()&&!dormant()&&getTarget()!=null&&!isMoveRunning();}
        @Override public boolean requiresUpdateEveryTick(){return true;}
        @Override public void tick(){
            var target=getTarget();if(target==null||CombatReactionService.isStaggered(LegacyBeastEntity.this))return;
            if(tryStartMove(target))return;
            if(tickCount%rules().ticks("decision_ticks")==0){
                var away=position().subtract(target.position()).multiply(1,0,1).normalize();var side=new Vec3(-away.z,0,away.x);
                var at=target.position().add(away.scale(rules().number("sword_orbit_radius"))).add(side.scale(rules().number("sword_orbit_offset")));getNavigation().moveTo(at.x,at.y,at.z,rules().number("sword_orbit_speed"));
            }
        }
    }
}
