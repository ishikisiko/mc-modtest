package com.example.myvillage.client.combat;

import com.example.myvillage.combat.CombatMode;
import com.example.myvillage.combat.definition.BasicSwordStyle;
import com.example.myvillage.combat.definition.MoveFeedback;
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
 * curve as the held item without keeping any frame history, and it pauses with the hit-stop
 * because it reads the same visual tick as the viewmodel.
 *
 * <p>The ribbon is alpha-blended and tapers with age: the newest sample covers the outer 45% of
 * the blade, older samples shrink to a sliver at the tip, and a near-white edge band runs along
 * the tip's path. Thrusts sweep no area, so they draw one camera-facing streak along the blade.
 */
public final class FirstPersonSwordTrail {
    static final float TRAIL_TICKS = 1.2F;
    static final float FADE_TICKS = 2.4F;
    static final int SEGMENTS = 24;
    /** A thrust streak fades from 0.8 to 0 over this many visual ticks after the strike starts. */
    static final float STREAK_TICKS = 3.0F;
    private static final float STREAK_HALF_WIDTH = 0.012F;
    /** The thrust streak starts this far up the blade and overshoots the tip by this share. */
    private static final float STREAK_START = 0.35F;
    private static final float STREAK_OVERSHOOT = 0.15F;
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
        HumanoidArm arm = player.getMainArm();
        ItemStack stack = event.getItemStack();
        BakedModel model = minecraft.getItemRenderer().getModel(stack, player.level(), player, player.getId());
        VertexConsumer consumer = event.getMultiBufferSource().getBuffer(CombatRenderTypes.SWORD_TRAIL_TRANSLUCENT);

        boolean thrust = BasicSwordStyle.feedback(frame.get().moveIndex()).swingSound()
                == MoveFeedback.SwingSound.THRUST;
        if (thrust) {
            float alpha = SwordTrailShape.streakAlpha(now, move.strikeStartTick(), STREAK_TICKS);
            if (alpha <= 0.0F) {
                return;
            }
            Vector3f[] blade = bladePoints(
                    event.getPoseStack(), arm, event.getEquipProgress(), swing.get(),
                    move.sample(Math.min(now, move.strikeEndTick())), model);
            Vector3f length = new Vector3f(blade[1]).sub(blade[0]);
            Vector3f start = new Vector3f(blade[0]).add(new Vector3f(length).mul(STREAK_START));
            Vector3f end = new Vector3f(blade[1]).add(new Vector3f(length).mul(STREAK_OVERSHOOT));
            CombatRenderTypes.streak(consumer, start, end, STREAK_HALF_WIDTH, alpha);
            return;
        }

        float newest = Math.min(now, move.strikeEndTick());
        float oldest = Math.max(move.strikeStartTick(), now - TRAIL_TICKS);
        if (newest <= oldest) {
            return;
        }
        float fade = SwordTrailShape.fade(now, move.strikeEndTick(), FADE_TICKS);
        if (fade <= 0.0F) {
            return;
        }
        Vector3f[] previous = null;
        float previousAge = 0.0F;
        float previousAlpha = 0.0F;
        for (int index = 0; index <= SEGMENTS; index++) {
            float tick = newest - (newest - oldest) * index / SEGMENTS;
            float age = (now - tick) / TRAIL_TICKS;
            float alpha = SwordTrailShape.alpha(age, fade);
            Vector3f[] blade = bladePoints(
                    event.getPoseStack(), arm, event.getEquipProgress(), swing.get(),
                    move.sample(tick), model);
            if (previous != null) {
                CombatRenderTypes.trailSegment(
                        consumer,
                        previous[0], previous[1], previousAge, previousAlpha,
                        blade[0], blade[1], age, alpha);
            }
            previous = blade;
            previousAge = age;
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
