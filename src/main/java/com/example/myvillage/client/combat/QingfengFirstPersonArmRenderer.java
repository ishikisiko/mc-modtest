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
 * Draws the first-person sword arm (skin plus sleeve) as upper arm, forearm and fist: the fist
 * closes around the Qingfeng handle, the forearm meets it at a solved wrist, and the upper arm runs
 * off-screen to the shoulder. It uses exactly the pose the held item uses this frame, plus the
 * presentation-only wrist lag from {@link FirstPersonArmLag}. The event is never cancelled, so
 * vanilla still draws the sword itself.
 */
public final class QingfengFirstPersonArmRenderer {
    /**
     * Cross-section scales against the skin arm: the fist (palm) is a little wider than the arm and
     * the forearm narrows toward it, so the wrist reads as a joint instead of one long block.
     */
    static final float FIST_WIDTH = 1.1F;
    static final float FOREARM_WIDTH = 0.8F;
    /** Joint overlaps, in skin pixels, so bends never open a gap. */
    static final float ELBOW_OVERLAP_PIXELS = 1.0F;
    static final float WRIST_OVERLAP_PIXELS = 1.0F;
    static final float SHOULDER_TO_ELBOW_OVERLAP_PIXELS = 0.5F;
    /** After an interrupted move the last lag relaxes over about this many ticks instead of snapping. */
    static final float LAG_RELAX_TICKS = 1.5F;

    private static final Vector3f lastLag = new Vector3f();
    private static double lastLagTime = Double.NEGATIVE_INFINITY;

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
        Vector3f lag = lag(player, event.getPartialTick(), swing.get());
        HumanoidArm arm = player.getMainArm();
        QingfengFirstPersonArmIk.Solution solution = QingfengFirstPersonArmIk.solve(
                arm, event.getEquipProgress(), swing.get(), pose.get(), lag);
        PlayerSkin skin = player.getSkin();
        QingfengFirstPersonArmModel model = model(skin.model() == PlayerSkin.Model.SLIM, arm);
        PlayerModelPart sleevePart = arm == HumanoidArm.RIGHT
                ? PlayerModelPart.RIGHT_SLEEVE
                : PlayerModelPart.LEFT_SLEEVE;
        render(event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight(),
                skin.texture(), model, swing.get().rig().arm(), solution, player.isModelPartShown(sleevePart));
    }

    /**
     * The wrist lag for this frame: from the playing move, or the last move's lag relaxing to zero
     * when a move was interrupted or cross-faded away.
     */
    private static Vector3f lag(LocalPlayer player, float partialTick, FirstPersonSwing swing) {
        double now = player.level().getGameTime() + partialTick;
        Optional<QingfengFirstPersonAnimator.Frame> frame =
                QingfengFirstPersonAnimator.INSTANCE.currentFrame(player, partialTick);
        if (frame.isPresent()) {
            Vector3f lag = FirstPersonArmLag.offset(swing, swing.move(frame.get().moveIndex()), frame.get().tick());
            lastLag.set(lag);
            lastLagTime = now;
            return lag;
        }
        double elapsed = now - lastLagTime;
        if (!(elapsed >= 0.0) || lastLag.lengthSquared() < 1.0E-10F) {
            return null;
        }
        return new Vector3f(lastLag).mul((float) Math.exp(-elapsed / LAG_RELAX_TICKS));
    }

    private static void render(
            PoseStack poseStack,
            MultiBufferSource buffers,
            int packedLight,
            ResourceLocation texture,
            QingfengFirstPersonArmModel model,
            FirstPersonSwing.Arm arm,
            QingfengFirstPersonArmIk.Solution solution,
            boolean sleeveShown) {
        VertexConsumer skin = buffers.getBuffer(RenderType.entitySolid(texture));
        drawArm(poseStack, skin, packedLight, model, arm, solution, false, 0.0F);
        if (sleeveShown) {
            VertexConsumer sleeve = buffers.getBuffer(RenderType.entityTranslucent(texture));
            drawArm(poseStack, sleeve, packedLight, model, arm, solution, true,
                    QingfengFirstPersonArmModel.SLEEVE_INFLATION_PIXELS);
        }
    }

    private static void drawArm(
            PoseStack poseStack,
            VertexConsumer consumer,
            int packedLight,
            QingfengFirstPersonArmModel model,
            FirstPersonSwing.Arm arm,
            QingfengFirstPersonArmIk.Solution solution,
            boolean sleeve,
            float inflationPixels) {
        float thickness = arm.thickness();
        float pixel = thickness / 16.0F;
        float width = thickness * (model.widthPixels() + 2.0F * inflationPixels) / model.widthPixels();
        float depth = thickness * (QingfengFirstPersonArmModel.DEPTH_PIXELS + 2.0F * inflationPixels)
                / QingfengFirstPersonArmModel.DEPTH_PIXELS;
        float extra = inflationPixels * pixel;

        float upperLength = arm.upperArm() + SHOULDER_TO_ELBOW_OVERLAP_PIXELS * pixel + extra;
        segment(poseStack, consumer, packedLight, model.upper(sleeve), solution.shoulder(),
                solution.upperArmRotation(), 0.0F,
                width, upperLength * 16.0F / QingfengFirstPersonArmModel.UPPER_ARM_TEXTURE_PIXELS, depth);

        float back = ELBOW_OVERLAP_PIXELS * pixel + extra;
        float forearmLength = back + arm.forearm() + WRIST_OVERLAP_PIXELS * pixel + extra;
        segment(poseStack, consumer, packedLight, model.forearm(sleeve), solution.elbow(),
                solution.forearmRotation(), -back,
                width * FOREARM_WIDTH, forearmLength * 16.0F / QingfengFirstPersonArmModel.FOREARM_TEXTURE_PIXELS,
                depth * FOREARM_WIDTH);

        float fistLength = QingfengFirstPersonArmIk.FIST_LENGTH_PIXELS;
        float fistScale = thickness * (fistLength + 2.0F * inflationPixels) / fistLength;
        segment(poseStack, consumer, packedLight, model.fist(sleeve), solution.wrist(),
                solution.fistRotation(), 0.0F, width * FIST_WIDTH, fistScale, depth * FIST_WIDTH);
    }

    private static void segment(
            PoseStack poseStack,
            VertexConsumer consumer,
            int packedLight,
            ModelPart part,
            Vector3f joint,
            Quaternionf rotation,
            float alongOffset,
            float widthScale,
            float lengthScale,
            float depthScale) {
        poseStack.pushPose();
        poseStack.translate(joint.x, joint.y, joint.z);
        poseStack.mulPose(rotation);
        poseStack.translate(0.0F, alongOffset, 0.0F);
        poseStack.scale(widthScale, lengthScale, depthScale);
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
