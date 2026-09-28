package com.example.myvillage.client.combat;

import com.example.myvillage.combat.CombatMode;
import com.example.myvillage.item.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Optional;

/**
 * Draws the first-person 剑光 ribbon swept by the Qingfeng blade during each move's strike
 * window. The blade is re-posed at earlier visual ticks, so the ribbon follows the exact same
 * curve as the held item without keeping any frame history.
 */
public final class FirstPersonSwordTrail {
    static final float TRAIL_TICKS = 1.8F;
    static final float FADE_TICKS = 1.6F;
    private static final int SEGMENTS = 14;
    // Qingfeng sprite coordinates (64x64 texture) just above the guard and at the tip.
    private static final Vector3f BLADE_BASE = new Vector3f(24.0F / 64.0F, 1.0F - 42.0F / 64.0F, 0.5F);
    private static final Vector3f BLADE_TIP = new Vector3f(60.0F / 64.0F, 1.0F - 2.0F / 64.0F, 0.5F);

    private FirstPersonSwordTrail() {
    }

    public static void onRenderHand(RenderHandEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null
                || event.getHand() != InteractionHand.MAIN_HAND
                || player.isInvisible()
                || !event.getItemStack().is(ModItems.QINGFENG_SWORD.get())
                || ClientCombatState.mode() != CombatMode.CULTIVATION) {
            return;
        }
        Optional<FirstPersonSwing> swing = FirstPersonSwingResources.current();
        Optional<QingfengFirstPersonAnimator.Frame> frame = QingfengFirstPersonAnimator.INSTANCE
                .currentFrame(player, event.getPartialTick());
        if (swing.isEmpty() || frame.isEmpty()) {
            return;
        }
        FirstPersonSwing.Move move = swing.get().move(frame.get().moveIndex());
        float now = frame.get().tick();
        float newest = Math.min(now, move.strikeEndTick());
        float oldest = Math.max(move.strikeStartTick(), now - TRAIL_TICKS);
        if (newest <= oldest) {
            return;
        }
        float fade = now <= move.strikeEndTick()
                ? 1.0F
                : 1.0F - (now - move.strikeEndTick()) / FADE_TICKS;
        if (fade <= 0.0F) {
            return;
        }

        HumanoidArm arm = player.getMainArm();
        ItemStack stack = event.getItemStack();
        BakedModel model = minecraft.getItemRenderer().getModel(stack, player.level(), player, player.getId());
        VertexConsumer consumer = event.getMultiBufferSource().getBuffer(CombatRenderTypes.SWORD_TRAIL);
        Vector3f[] previous = null;
        float previousAlpha = 0.0F;
        for (int index = 0; index <= SEGMENTS; index++) {
            float tick = newest - (newest - oldest) * index / SEGMENTS;
            float age = (now - tick) / TRAIL_TICKS;
            float alpha = (float) Math.pow(Math.max(0.0F, 1.0F - age), 1.5) * fade * 0.85F;
            Vector3f[] blade = bladePoints(
                    event.getPoseStack(), arm, event.getEquipProgress(), swing.get(),
                    move.sample(tick), model);
            if (previous != null) {
                CombatRenderTypes.ribbonQuad(
                        consumer, previous[0], previous[1], previousAlpha, blade[0], blade[1], alpha);
            }
            previous = blade;
            previousAlpha = alpha;
        }
    }

    private static Vector3f[] bladePoints(
            PoseStack poseStack,
            HumanoidArm arm,
            float equipProgress,
            FirstPersonSwing swing,
            FirstPersonSwing.Pose pose,
            BakedModel model) {
        boolean leftHand = arm == HumanoidArm.LEFT;
        poseStack.pushPose();
        FirstPersonSwordTransform.apply(poseStack, arm, equipProgress, swing.rig(), pose);
        model.applyTransform(
                leftHand ? ItemDisplayContext.FIRST_PERSON_LEFT_HAND : ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
                poseStack,
                leftHand);
        poseStack.translate(-0.5F, -0.5F, -0.5F);
        Matrix4f matrix = new Matrix4f(poseStack.last().pose());
        poseStack.popPose();
        return new Vector3f[] {
                matrix.transformPosition(new Vector3f(BLADE_BASE)),
                matrix.transformPosition(new Vector3f(BLADE_TIP))
        };
    }
}
