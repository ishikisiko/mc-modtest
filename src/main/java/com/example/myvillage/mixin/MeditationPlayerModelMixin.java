package com.example.myvillage.mixin;

import com.example.myvillage.client.cultivation.motion.ClientMeditationMotion;
import com.example.myvillage.client.cultivation.motion.MeditationPose;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Runs after PAL's normal tail: a server-confirmed meditation owns the body only until interruption. */
@Mixin(value=PlayerModel.class,priority=900)
public abstract class MeditationPlayerModelMixin {
    @Inject(method="setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V",at=@At("TAIL"))
    private void myvillage$pose(LivingEntity entity,float swing,float amount,float age,float yaw,float pitch,CallbackInfo ci) {
        PlayerModel<?> model=(PlayerModel<?>)(Object)this;
        MeditationPose.clear(model);
        if(entity instanceof AbstractClientPlayer player) {
            MeditationPose pose=ClientMeditationMotion.pose(player,Math.clamp(age-player.tickCount,0,1));
            if(pose!=null){pose.apply(model,ClientMeditationMotion.definition());ClientMeditationMotion.observed(player,pose);}
        }
    }
}
