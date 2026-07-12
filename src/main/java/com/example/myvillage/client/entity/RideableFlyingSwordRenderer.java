package com.example.myvillage.client.entity;

import com.example.myvillage.entity.RideableFlyingSwordEntity;
import com.example.myvillage.item.ModItems;
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
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class RideableFlyingSwordRenderer extends EntityRenderer<RideableFlyingSwordEntity> {
    private static final float MODEL_SCALE = 2.5F;
    private static final float TEXTURE_AXIS_ALIGNMENT_DEGREES = 45.0F;

    private final ItemRenderer itemRenderer;
    private final ItemStack swordStack;

    public RideableFlyingSwordRenderer(EntityRendererProvider.Context context) {
        super(context);
        itemRenderer = context.getItemRenderer();
        swordStack = new ItemStack(ModItems.RIDEABLE_FLYING_SWORD.get());
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
        poseStack.translate(0.0F, 0.15F, 0.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees(-entityYaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(TEXTURE_AXIS_ALIGNMENT_DEGREES));
        poseStack.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
        itemRenderer.renderStatic(
                swordStack,
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
