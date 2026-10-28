package com.example.myvillage.mixin;

import com.example.myvillage.client.cultivation.motion.ClientMeditationMotion;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Rested hands do not display held weapons. Inventory and selected slots are never modified. */
@Mixin(ItemInHandLayer.class)
public abstract class MeditationHeldItemMixin {
    @Inject(method="render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",at=@At("HEAD"),cancellable=true)
    private void myvillage$hide(PoseStack poses,MultiBufferSource buffers,int light,LivingEntity entity,
            float swing,float amount,float partial,float age,float yaw,float pitch,CallbackInfo ci) {
        if(entity instanceof AbstractClientPlayer player&&ClientMeditationMotion.displaying(player))ci.cancel();
    }
}
