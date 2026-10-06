package com.example.myvillage.client.combat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Third person: the second of a paired weapon (see {@link PairedWeapons}) on the player's off hand,
 * drawn while the off-hand slot is empty. It follows the off arm exactly as vanilla's
 * {@code ItemInHandLayer} places an item in that hand (so the PAL pose of the left arm, its guard,
 * chamber and strike roles included, carries it), with the model's own
 * {@code thirdperson_righthand} display transform mirrored across the arm's side: the left hand
 * wears the mirror image of the right. Presentation only.
 */
final class PairedWeaponLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    PairedWeaponLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    /** Adds the layer to the wide and the slim player renderer. */
    static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        for (PlayerSkin.Model skin : event.getSkins()) {
            EntityRenderer<? extends Player> renderer = event.getSkin(skin);
            if (renderer instanceof PlayerRenderer playerRenderer) {
                playerRenderer.addLayer(new PairedWeaponLayer(playerRenderer));
            }
        }
    }

    @Override
    public void render(
            PoseStack poseStack,
            MultiBufferSource buffers,
            int packedLight,
            AbstractClientPlayer player,
            float limbSwing,
            float limbSwingAmount,
            float partialTick,
            float ageInTicks,
            float netHeadYaw,
            float headPitch) {
        if (player.isInvisible()) {
            return;
        }
        ItemStack stack = PairedWeapons.offHandCopy(player);
        if (stack.isEmpty()) {
            return;
        }
        HumanoidArm offArm = player.getMainArm().getOpposite();
        boolean mirrored = PairedWeapons.mirrored(offArm);
        poseStack.pushPose();
        // ItemInHandLayer.renderArmWithItem for the off arm.
        getParentModel().translateToHand(offArm, poseStack);
        poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        poseStack.translate((offArm == HumanoidArm.LEFT ? -1.0F : 1.0F) / 16.0F, 0.125F, -0.625F);
        if (mirrored) {
            // The left hand's item frame is the right's reflected across the arm's side; reflecting
            // the right-hand placement with it gives the left gauntlet, not a right one turned round.
            poseStack.scale(-1.0F, 1.0F, 1.0F);
        }
        BakedModel model = PairedWeapons.model(stack, player, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND);
        model.getTransforms().getTransform(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND).apply(false, poseStack);
        poseStack.translate(-0.5F, -0.5F, -0.5F);
        PairedWeapons.renderModel(stack, model, mirrored, poseStack, buffers, packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }
}
