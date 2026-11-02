package com.example.myvillage.mixin;

import com.example.myvillage.combat.runtime.CombatActors;
import com.example.myvillage.entity.npc.CultivatorEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Expand existing vanilla villager hunts; their timing, priority and targeting conditions remain authoritative. */
@Mixin(value = NearestAttackableTargetGoal.class, remap = false)
public abstract class VillagerLikeTargetMixin extends TargetGoal {
    @Shadow @Final protected Class<? extends LivingEntity> targetType;
    @Shadow protected LivingEntity target;
    @Shadow protected TargetingConditions targetConditions;
    @Shadow protected abstract AABB getTargetSearchArea(double distance);

    protected VillagerLikeTargetMixin(Mob mob, boolean mustSee) { super(mob, mustSee); }

    @Inject(method = "findTarget()V", at = @At("HEAD"), cancellable = true, require = 1, allow = 1, remap = false)
    private void myvillage$includeCultivators(CallbackInfo callback) {
        if (!(mob instanceof Enemy) || !AbstractVillager.class.isAssignableFrom(targetType)
                || !BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).getNamespace().equals("minecraft")) return;
        target = mob.level().getNearestEntity(
                mob.level().getEntitiesOfClass(LivingEntity.class, getTargetSearchArea(getFollowDistance()),
                        candidate -> (targetType.isInstance(candidate) || candidate instanceof CultivatorEntity)
                                && CombatActors.mayHarm(mob, candidate)),
                targetConditions, mob, mob.getX(), mob.getEyeY(), mob.getZ());
        callback.cancel();
    }
}
