package com.example.myvillage.client.entity;

import com.example.myvillage.entity.RideableFlyingSwordEntity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class RideableFlyingSwordRenderer extends EntityRenderer<RideableFlyingSwordEntity> {

    private final ItemRenderer itemRenderer;

    public RideableFlyingSwordRenderer(EntityRendererProvider.Context context) {
        super(context);
        itemRenderer = context.getItemRenderer();
        shadowRadius = 0.5F;
    }

    @Override
    public void render(
            RideableFlyingSwordEntity entity,
            float entityYaw,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight) {
        poseStack.pushPose();
        poseStack.translate(0.0F, FlyingSwordVisuals.get().model_y_offset(), 0.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees(-entityYaw));
        float bank = net.minecraft.util.Mth.clamp(net.minecraft.util.Mth.wrapDegrees(entity.getYRot()-entity.yRotO), -FlyingSwordVisuals.get().max_bank_degrees(), FlyingSwordVisuals.get().max_bank_degrees());
        poseStack.mulPose(Axis.ZP.rotationDegrees(-bank));
        poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
        float scale=FlyingSwordVisuals.get().model_scale();
        poseStack.scale(scale, scale, scale);
        itemRenderer.renderStatic(
                entity.visualStack(),
                ItemDisplayContext.NONE,
                packedLight,
                OverlayTexture.NO_OVERLAY,
                poseStack,
                buffer,
                entity.level(),
                entity.getId());
        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(RideableFlyingSwordEntity entity) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
