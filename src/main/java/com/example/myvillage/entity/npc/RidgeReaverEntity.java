package com.example.myvillage.entity.npc;

import com.example.myvillage.wilderness.ExpeditionRules;
import com.example.myvillage.wilderness.ExpeditionCombat;
import com.example.myvillage.combat.session.CombatSessionManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.syncher.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.ai.goal.target.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.Level;
import net.minecraft.sounds.*;
import java.util.List;

/** Ordinary hostile human. Intent, sword hits and equipment still belong to the shared executor. */
public final class RidgeReaverEntity extends NpcEntity {
    public static final ResourceLocation ID=ResourceLocation.fromNamespaceAndPath("myvillage", "ridge_reaver");
    public static final List<String> LOOKS=List.of("default");
    private static final EntityDataAccessor<Boolean> GUARD=SynchedEntityData.defineId(RidgeReaverEntity.class,EntityDataSerializers.BOOLEAN);
    public RidgeReaverEntity(EntityType<? extends RidgeReaverEntity> type,Level level){super(type,level);xpReward=ExpeditionRules.get().mob(ID.getPath()).get("experience").getAsInt();}
    public static AttributeSupplier.Builder createAttributes(){var a=ExpeditionRules.get().mob(ID.getPath()).getAsJsonObject("attributes");return Mob.createMobAttributes().add(Attributes.MAX_HEALTH,a.get("max_health").getAsDouble()).add(Attributes.ATTACK_DAMAGE,a.get("attack_damage").getAsDouble()).add(Attributes.ATTACK_KNOCKBACK,0).add(Attributes.MOVEMENT_SPEED,a.get("movement_speed").getAsDouble()).add(Attributes.FOLLOW_RANGE,a.get("follow_range").getAsDouble()).add(Attributes.ARMOR,a.get("armor").getAsDouble());}
    protected List<String> looks(){return LOOKS;}
    protected double strollSpeedModifier(){return .65;}
    protected void defineSynchedData(SynchedEntityData.Builder b){super.defineSynchedData(b);b.define(GUARD,false);}
    protected void registerGoals(){super.registerGoals();targetSelector.addGoal(1,new HurtByTargetGoal(this));targetSelector.addGoal(2,new NearestAttackableTargetGoal<>(this,Player.class,true));}
    @Override public void setTarget(LivingEntity target){if(target!=null&&target!=getTarget()){combatAnchor=position();lastThreatTick=level().getGameTime();nextCombatDecision=lastThreatTick+NpcCombatRules.get().reactionTicks();}super.setTarget(target);}
    public boolean guarding(){return entityData.get(GUARD);}
    @Override protected void customServerAiStep(){super.customServerAiStep();var r=ExpeditionRules.get();boolean guard=getTarget()!=null&&!CombatSessionManager.hasAction(this)&&!com.example.myvillage.combat.runtime.CombatReactionService.isStaggered(this)&&tickCount%r.ticks("guard_cycle")<r.ticks("guard_ticks");entityData.set(GUARD,guard);}
    @Override public boolean hurt(DamageSource source,float amount){if(guarding()&&source.getEntity() instanceof LivingEntity attacker&&ExpeditionCombat.front(this,attacker)&&!source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_SHIELD)&&!source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)){amount*=1-ExpeditionRules.get().number("guard_fraction");playSound(SoundEvents.SHIELD_BLOCK,.65F,1.2F);}return super.hurt(source,amount);}
    @Override public boolean removeWhenFarAway(double distance){return getPersistentData().getBoolean("myvillage_roster_natural");}
    @Override protected SoundEvent getHurtSound(DamageSource source){return SoundEvents.PLAYER_HURT;}
    @Override protected SoundEvent getDeathSound(){return SoundEvents.PLAYER_DEATH;}
}
