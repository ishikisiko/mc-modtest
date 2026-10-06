package com.example.myvillage.client.combat;

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
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Optional;

/**
 * Draws the first-person weapon arm (skin plus sleeve) as upper arm, forearm and fist: the fist
 * closes around the weapon's handle, the forearm meets it at a solved wrist, and the upper arm runs
 * off-screen to the shoulder. It uses exactly the pose the held item uses this frame, plus the
 * presentation-only wrist lag from {@link FirstPersonArmLag}. The event is never cancelled, so
 * vanilla still draws the weapon itself.
 *
 * <p>A rig with {@code rig.off_hand} also gets the off arm, its hand on the shaft (see
 * {@link FirstPersonArmIk#solveOffHand}), drawn with the off arm's skin and sleeve, but only while
 * the off-hand slot is empty: an off-hand item keeps its own vanilla hand pass and the second arm
 * is not drawn. The off arm takes no lag, so its hand never leaves the shaft.
 *
 * <p>With {@code rig.off_hand.free} the off hand is bare and drawn at its keyed rest; for a
 * {@code paired} weapon (0.39.1) the same item model is drawn over that fist as well, mirrored on a
 * left hand ({@link FirstPersonWeaponTransform#pairedItem}).
 */
public final class FirstPersonArmRenderer {
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

    private static FirstPersonArmModel wideRight;
    private static FirstPersonArmModel wideLeft;
    private static FirstPersonArmModel slimRight;
    private static FirstPersonArmModel slimLeft;

    private FirstPersonArmRenderer() {
    }

    public static void onRenderHand(RenderHandEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null
                || event.getHand() != InteractionHand.MAIN_HAND
                || player.isInvisible()
                || player.isScoping()) {
            return;
        }
        Optional<FirstPersonSwing> swing = FirstPersonSwingResources.forStack(event.getItemStack())
                .map(FirstPersonSwingResources.WeaponRig::swing);
        Optional<FirstPersonSwing.Pose> pose = FirstPersonWeaponAnimator.displayedPose(player, event.getPartialTick());
        if (swing.isEmpty() || pose.isEmpty()) {
            return;
        }
        Vector3f lag = lag(player, event.getPartialTick(), swing.get());
        HumanoidArm arm = player.getMainArm();
        FirstPersonArmIk.Solution solution = FirstPersonArmIk.solve(
                arm, event.getEquipProgress(), swing.get(), pose.get(), lag);
        PlayerSkin skin = player.getSkin();
        FirstPersonArmModel model = model(skin.model() == PlayerSkin.Model.SLIM, arm);
        PlayerModelPart sleevePart = arm == HumanoidArm.RIGHT
                ? PlayerModelPart.RIGHT_SLEEVE
                : PlayerModelPart.LEFT_SLEEVE;
        render(event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight(),
                skin.texture(), model, swing.get().rig().arm(), solution, player.isModelPartShown(sleevePart));

        if (swing.get().rig().offHand() == null || !player.getOffhandItem().isEmpty()) {
            return;
        }
        Optional<FirstPersonArmIk.OffHandSolution> offHand = FirstPersonArmIk.solveOffHand(
                arm, event.getEquipProgress(), swing.get(), pose.get());
        if (offHand.isPresent()) {
            HumanoidArm offArm = arm.getOpposite();
            PlayerModelPart offSleeve = offArm == HumanoidArm.RIGHT
                    ? PlayerModelPart.RIGHT_SLEEVE
                    : PlayerModelPart.LEFT_SLEEVE;
            render(event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight(),
                    skin.texture(), model(skin.model() == PlayerSkin.Model.SLIM, offArm), swing.get().rig().offArm(),
                    offHand.get().arm(), player.isModelPartShown(offSleeve));
            if (offHand.get().free() && PairedWeapons.isPaired(event.getItemStack())) {
                renderPaired(event, player, arm, swing.get(), offHand.get().arm());
            }
        }
    }

    /**
     * A paired weapon's second on the free off hand (see {@link PairedWeapons} and
     * {@link FirstPersonWeaponTransform#pairedItem}): the same item model, mirrored for a left off
     * hand, over the fist just drawn.
     */
    private static void renderPaired(
            RenderHandEvent event,
            LocalPlayer player,
            HumanoidArm mainArm,
            FirstPersonSwing swing,
            FirstPersonArmIk.Solution offArm) {
        ItemStack stack = event.getItemStack();
        Matrix4f placement = FirstPersonWeaponTransform.pairedItem(mainArm, swing, offArm);
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.mulPose(placement);
        PairedWeapons.renderModel(stack,
                PairedWeapons.model(stack, player, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND),
                placement.determinant() < 0.0F, poseStack, event.getMultiBufferSource(),
                event.getPackedLight(), OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }

    /**
     * The wrist lag for this frame: from the playing move, or the last move's lag relaxing to zero
     * when a move was interrupted or cross-faded away.
     */
    private static Vector3f lag(LocalPlayer player, float partialTick, FirstPersonSwing swing) {
        double now = ClientCombatClock.now(partialTick);
        Optional<FirstPersonWeaponAnimator.Frame> frame =
                FirstPersonWeaponAnimator.INSTANCE.currentFrame(player, partialTick);
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
            FirstPersonArmModel model,
            FirstPersonSwing.Arm arm,
            FirstPersonArmIk.Solution solution,
            boolean sleeveShown) {
        VertexConsumer skin = buffers.getBuffer(RenderType.entitySolid(texture));
        drawArm(poseStack, skin, packedLight, model, arm, solution, false, 0.0F);
        if (sleeveShown) {
            VertexConsumer sleeve = buffers.getBuffer(RenderType.entityTranslucent(texture));
            drawArm(poseStack, sleeve, packedLight, model, arm, solution, true,
                    FirstPersonArmModel.SLEEVE_INFLATION_PIXELS);
        }
    }

    private static void drawArm(
            PoseStack poseStack,
            VertexConsumer consumer,
            int packedLight,
            FirstPersonArmModel model,
            FirstPersonSwing.Arm arm,
            FirstPersonArmIk.Solution solution,
            boolean sleeve,
            float inflationPixels) {
        float thickness = arm.thickness();
        float pixel = thickness / 16.0F;
        float width = thickness * (model.widthPixels() + 2.0F * inflationPixels) / model.widthPixels();
        float depth = thickness * (FirstPersonArmModel.DEPTH_PIXELS + 2.0F * inflationPixels)
                / FirstPersonArmModel.DEPTH_PIXELS;
        float extra = inflationPixels * pixel;

        float upperLength = arm.upperArm() + SHOULDER_TO_ELBOW_OVERLAP_PIXELS * pixel + extra;
        segment(poseStack, consumer, packedLight, model.upper(sleeve), solution.shoulder(),
                solution.upperArmRotation(), 0.0F,
                width, upperLength * 16.0F / FirstPersonArmModel.UPPER_ARM_TEXTURE_PIXELS, depth);

        float back = ELBOW_OVERLAP_PIXELS * pixel + extra;
        float forearmLength = back + arm.forearm() + WRIST_OVERLAP_PIXELS * pixel + extra;
        segment(poseStack, consumer, packedLight, model.forearm(sleeve), solution.elbow(),
                solution.forearmRotation(), -back,
                width * FOREARM_WIDTH, forearmLength * 16.0F / FirstPersonArmModel.FOREARM_TEXTURE_PIXELS,
                depth * FOREARM_WIDTH);

        float fistLength = FirstPersonArmIk.FIST_LENGTH_PIXELS;
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

    private static FirstPersonArmModel model(boolean slim, HumanoidArm arm) {
        if (arm == HumanoidArm.RIGHT) {
            if (slim) {
                if (slimRight == null) {
                    slimRight = FirstPersonArmModel.create(true, arm);
                }
                return slimRight;
            }
            if (wideRight == null) {
                wideRight = FirstPersonArmModel.create(false, arm);
            }
            return wideRight;
        }
        if (slim) {
            if (slimLeft == null) {
                slimLeft = FirstPersonArmModel.create(true, arm);
            }
            return slimLeft;
        }
        if (wideLeft == null) {
            wideLeft = FirstPersonArmModel.create(false, arm);
        }
        return wideLeft;
    }
}
