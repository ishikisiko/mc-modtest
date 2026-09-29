package com.example.myvillage.client.combat;

import com.example.myvillage.item.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Optional;

/**
 * Draws the complete first-person sword arm (skin plus sleeve) from an off-screen shoulder to the
 * Qingfeng grip, using exactly the pose the held item uses this frame. Presentation only; the
 * event is never cancelled, so vanilla still draws the sword itself.
 */
public final class QingfengFirstPersonArmRenderer {
    /** Cross-section scale; the viewmodel arm sits closer to the eye than the vanilla one. */
    static final float THICKNESS = 0.85F;

    private static QingfengFirstPersonArmModel wideRight;
    private static QingfengFirstPersonArmModel wideLeft;
    private static QingfengFirstPersonArmModel slimRight;
    private static QingfengFirstPersonArmModel slimLeft;

    private QingfengFirstPersonArmRenderer() {
    }

    public static void onRenderHand(RenderHandEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null
                || event.getHand() != InteractionHand.MAIN_HAND
                || player.isInvisible()
                || player.isScoping()
                || !event.getItemStack().is(ModItems.QINGFENG_SWORD.get())) {
            return;
        }
        Optional<FirstPersonSwing> swing = FirstPersonSwingResources.current();
        Optional<FirstPersonSwing.Pose> pose = QingfengFirstPersonAnimator.displayedPose(player, event.getPartialTick());
        if (swing.isEmpty() || pose.isEmpty()) {
            return;
        }
        HumanoidArm arm = player.getMainArm();
        QingfengFirstPersonArmIk.Solution solution = QingfengFirstPersonArmIk.solve(
                arm, event.getEquipProgress(), swing.get().rig(), pose.get());
        PlayerSkin skin = player.getSkin();
        QingfengFirstPersonArmModel model = model(skin.model() == PlayerSkin.Model.SLIM, arm);
        PlayerModelPart sleevePart = arm == HumanoidArm.RIGHT
                ? PlayerModelPart.RIGHT_SLEEVE
                : PlayerModelPart.LEFT_SLEEVE;
        render(event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight(),
                skin.texture(), model, solution, player.isModelPartShown(sleevePart));
    }

    private static void render(
            PoseStack poseStack,
            MultiBufferSource buffers,
            int packedLight,
            ResourceLocation texture,
            QingfengFirstPersonArmModel model,
            QingfengFirstPersonArmIk.Solution solution,
            boolean sleeveShown) {
        VertexConsumer skin = buffers.getBuffer(RenderType.entitySolid(texture));
        segment(poseStack, skin, packedLight, model.upper(false), solution.shoulder(),
                solution.upperArmRotation(), 1.0F, 1.0F, 1.0F);
        segment(poseStack, skin, packedLight, model.forearm(false), solution.elbow(),
                solution.forearmRotation(), 1.0F, 1.0F, 1.0F);
        if (!sleeveShown) {
            return;
        }
        float inflation = QingfengFirstPersonArmModel.SLEEVE_INFLATION_PIXELS * 2.0F;
        float widthScale = (model.widthPixels() + inflation) / model.widthPixels();
        float depthScale = (QingfengFirstPersonArmModel.DEPTH_PIXELS + inflation)
                / QingfengFirstPersonArmModel.DEPTH_PIXELS;
        // The sleeve also reaches a quarter pixel past the fist so its cap never z-fights the skin.
        float upperLength = (QingfengFirstPersonArmModel.UPPER_ARM_PIXELS
                + QingfengFirstPersonArmModel.SLEEVE_INFLATION_PIXELS) / QingfengFirstPersonArmModel.UPPER_ARM_PIXELS;
        float forearmLength = (QingfengFirstPersonArmModel.FOREARM_END_PIXELS
                + QingfengFirstPersonArmModel.SLEEVE_INFLATION_PIXELS) / QingfengFirstPersonArmModel.FOREARM_END_PIXELS;
        VertexConsumer sleeve = buffers.getBuffer(RenderType.entityTranslucent(texture));
        segment(poseStack, sleeve, packedLight, model.upper(true), solution.shoulder(),
                solution.upperArmRotation(), widthScale, upperLength, depthScale);
        segment(poseStack, sleeve, packedLight, model.forearm(true), solution.elbow(),
                solution.forearmRotation(), widthScale, forearmLength, depthScale);
    }

    private static void segment(
            PoseStack poseStack,
            VertexConsumer consumer,
            int packedLight,
            ModelPart part,
            Vector3f joint,
            Quaternionf rotation,
            float widthScale,
            float lengthScale,
            float depthScale) {
        poseStack.pushPose();
        poseStack.translate(joint.x, joint.y, joint.z);
        poseStack.mulPose(rotation);
        poseStack.scale(THICKNESS * widthScale, lengthScale, THICKNESS * depthScale);
        part.render(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }

    private static QingfengFirstPersonArmModel model(boolean slim, HumanoidArm arm) {
        if (arm == HumanoidArm.RIGHT) {
            if (slim) {
                if (slimRight == null) {
                    slimRight = QingfengFirstPersonArmModel.create(true, arm);
                }
                return slimRight;
            }
            if (wideRight == null) {
                wideRight = QingfengFirstPersonArmModel.create(false, arm);
            }
            return wideRight;
        }
        if (slim) {
            if (slimLeft == null) {
                slimLeft = QingfengFirstPersonArmModel.create(true, arm);
            }
            return slimLeft;
        }
        if (wideLeft == null) {
            wideLeft = QingfengFirstPersonArmModel.create(false, arm);
        }
        return wideLeft;
    }
}
