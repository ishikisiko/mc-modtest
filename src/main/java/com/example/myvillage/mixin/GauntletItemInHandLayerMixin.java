package com.example.myvillage.mixin;

import com.example.myvillage.client.combat.WornGauntletRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandLayer.class)
public abstract class GauntletItemInHandLayerMixin {
    @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
    private void yuanxian$wearGauntlet(LivingEntity entity, ItemStack stack, ItemDisplayContext context,
                                      HumanoidArm arm, PoseStack poses, MultiBufferSource buffers,
                                      int light, CallbackInfo callback) {
        if (entity instanceof AbstractClientPlayer player
                && ((ItemInHandLayer<?, ?>) (Object) this).getParentModel() instanceof PlayerModel<?> model
                && WornGauntletRenderer.renderThirdPerson(player, model, arm, stack, poses, buffers, light)) {
            callback.cancel();
        }
    }
}
