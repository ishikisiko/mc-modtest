package com.example.myvillage.mixin;

import com.example.myvillage.client.legacy.LegacyVisuals;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandLayer.class)
public abstract class LegacyHeldItemMixin {
    @Inject(method="renderArmWithItem",at=@At("HEAD"),cancellable=true)
    private void myvillage$deployed(LivingEntity entity,ItemStack stack,ItemDisplayContext context,HumanoidArm arm,
            PoseStack poses,MultiBufferSource buffers,int light,CallbackInfo ci){
        if(arm==entity.getMainArm()&&LegacyVisuals.deployed(entity))ci.cancel();
    }
}
