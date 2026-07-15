package com.example.myvillage.client.combat;

import com.example.myvillage.combat.CombatMode;
import com.example.myvillage.item.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Optional;

public final class QingfengFirstPersonArmRenderer {
    private static QingfengFirstPersonArmModel wideRightArm;
    private static QingfengFirstPersonArmModel wideLeftArm;
    private static QingfengFirstPersonArmModel slimRightArm;
    private static QingfengFirstPersonArmModel slimLeftArm;

    private QingfengFirstPersonArmRenderer() {
    }

    public static void onRenderHand(RenderHandEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null
                || event.getHand() != InteractionHand.MAIN_HAND
                || player.isInvisible()
                || !event.getItemStack().is(ModItems.QINGFENG_SWORD.get())
                || !player.getMainHandItem().is(ModItems.QINGFENG_SWORD.get())
                || ClientCombatState.mode() != CombatMode.CULTIVATION) {
            return;
        }

        Optional<QingfengFirstPersonAnimator.Frame> frame = QingfengFirstPersonAnimator.INSTANCE
                .currentFrame(player, event.getPartialTick());
        FirstPersonSwordPose.Pose swordPose = frame
                .map(QingfengFirstPersonAnimator.Frame::pose)
                .orElse(FirstPersonSwordPose.neutral());
        FirstPersonArmPose.Pose armPose = frame
                .map(activeFrame -> FirstPersonArmPose.sample(
                        activeFrame.moveIndex(),
                        activeFrame.progress(),
                        swordPose))
                .orElse(FirstPersonArmPose.neutral(swordPose));
        renderArm(player, event, swordPose, armPose);
    }

    private static void renderArm(
            LocalPlayer player,
            RenderHandEvent event,
            FirstPersonSwordPose.Pose swordPose,
            FirstPersonArmPose.Pose pose) {
        HumanoidArm arm = player.getMainArm();
        float side = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        Matrix4f baseInverse = new Matrix4f(poseStack.last().pose()).invert();
        poseStack.pushPose();
        FirstPersonSwordTransform.apply(poseStack, arm, event.getEquipProgress(), swordPose);

        // Keep the articulated chain in the sword's grip frame before solving its joints.
        poseStack.translate(
                side * FirstPersonArmPose.GRIP_PIVOT_X,
                FirstPersonArmPose.GRIP_PIVOT_Y,
                FirstPersonArmPose.GRIP_PIVOT_Z);
        poseStack.mulPose(Axis.ZP.rotationDegrees(side * pose.counterRoll()));
        poseStack.mulPose(Axis.YP.rotationDegrees(side * pose.counterYaw()));
        poseStack.mulPose(Axis.XP.rotationDegrees(pose.counterPitch()));
        poseStack.translate(
                -side * FirstPersonArmPose.GRIP_PIVOT_X,
                -FirstPersonArmPose.GRIP_PIVOT_Y,
                -FirstPersonArmPose.GRIP_PIVOT_Z);
        poseStack.translate(
                side * FirstPersonArmPose.GRIP_OFFSET_X,
                FirstPersonArmPose.GRIP_OFFSET_Y,
                FirstPersonArmPose.GRIP_OFFSET_Z);
        poseStack.mulPose(Axis.YP.rotationDegrees(side * 45.0F));
        poseStack.translate(side * -1.0F, 3.6F, 3.5F);
        poseStack.mulPose(Axis.ZP.rotationDegrees(side * 120.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(200.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees(side * -135.0F));
        poseStack.translate(side * 5.6F, 0.0F, 0.0F);

        QingfengFirstPersonArmModel model = armModel(player, arm);
        Matrix4f gripFrame = baseInverse.mul(new Matrix4f(poseStack.last().pose()));
        Vector3f elbow = FirstPersonArmPose.scaledCorrectedElbow(pose, arm)
                .add(model.gripAnchorX(), model.rootY(), 0.0F)
                .div(16.0F);
        gripFrame.transformPosition(elbow);
        Vector3f gripCorrection = FirstPersonArmPose.gripCorrection(pose, arm);
        poseStack.translate(model.gripAnchorX() / 16.0F, model.gripAnchorY() / 16.0F, 0.0F);
        poseStack.scale(
                pose.viewmodelScale(),
                pose.viewmodelScale(),
                pose.viewmodelScale());
        poseStack.translate(-model.gripAnchorX() / 16.0F, -model.gripAnchorY() / 16.0F, 0.0F);
        poseStack.translate(
                gripCorrection.x / 16.0F,
                gripCorrection.y / 16.0F,
                gripCorrection.z / 16.0F);
        model.applyPose(pose, arm);
        model.renderSkin(
                poseStack,
                event.getMultiBufferSource().getBuffer(
                        RenderType.entitySolid(player.getSkin().texture())),
                event.getPackedLight(),
                OverlayTexture.NO_OVERLAY);
        PlayerModelPart sleevePart = arm == HumanoidArm.RIGHT
                ? PlayerModelPart.RIGHT_SLEEVE
                : PlayerModelPart.LEFT_SLEEVE;
        boolean sleeveShown = player.isModelPartShown(sleevePart);
        if (sleeveShown) {
            model.renderSleeve(
                    poseStack,
                    event.getMultiBufferSource().getBuffer(
                            RenderType.entityTranslucent(player.getSkin().texture())),
                    event.getPackedLight(),
                    OverlayTexture.NO_OVERLAY);
        }
        poseStack.popPose();
        renderConnector(
                player,
                event,
                model,
                new Vector3f(
                        Math.max(-0.88F, Math.min(0.88F, elbow.x + side * 0.16F)),
                        -0.92F - event.getEquipProgress() * 0.15F,
                        Math.min(-0.72F, elbow.z - 0.10F)),
                elbow,
                sleeveShown);
        poseStack.popPose();
    }

    private static void renderConnector(
            LocalPlayer player,
            RenderHandEvent event,
            QingfengFirstPersonArmModel model,
            Vector3f shoulder,
            Vector3f elbow,
            boolean sleeveShown) {
        Vector3f direction = new Vector3f(elbow).sub(shoulder);
        float length = direction.length();
        if (length <= 0.001F) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(shoulder.x, shoulder.y, shoulder.z);
        poseStack.mulPose(new Quaternionf().rotationTo(
                new Vector3f(0.0F, 1.0F, 0.0F),
                direction.div(length)));
        poseStack.scale(
                1.0F,
                length / QingfengFirstPersonArmModel.CONNECTOR_LENGTH_UNITS,
                1.0F);
        model.renderSkinConnector(
                poseStack,
                event.getMultiBufferSource().getBuffer(
                        RenderType.entitySolid(player.getSkin().texture())),
                event.getPackedLight(),
                OverlayTexture.NO_OVERLAY);
        if (sleeveShown) {
            model.renderSleeveConnector(
                    poseStack,
                    event.getMultiBufferSource().getBuffer(
                            RenderType.entityTranslucent(player.getSkin().texture())),
                    event.getPackedLight(),
                    OverlayTexture.NO_OVERLAY);
        }
        poseStack.popPose();
    }

    private static QingfengFirstPersonArmModel armModel(
            LocalPlayer player,
            HumanoidArm arm) {
        boolean slim = player.getSkin().model() == PlayerSkin.Model.SLIM;
        if (slim && arm == HumanoidArm.RIGHT) {
            if (slimRightArm == null) {
                slimRightArm = QingfengFirstPersonArmModel.create(true, arm);
            }
            return slimRightArm;
        }
        if (slim) {
            if (slimLeftArm == null) {
                slimLeftArm = QingfengFirstPersonArmModel.create(true, arm);
            }
            return slimLeftArm;
        }
        if (arm == HumanoidArm.RIGHT) {
            if (wideRightArm == null) {
                wideRightArm = QingfengFirstPersonArmModel.create(false, arm);
            }
            return wideRightArm;
        }
        if (wideLeftArm == null) {
            wideLeftArm = QingfengFirstPersonArmModel.create(false, arm);
        }
        return wideLeftArm;
    }
}
