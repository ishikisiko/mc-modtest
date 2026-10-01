package com.example.myvillage.client.combat;

import com.example.myvillage.combat.CombatMode;
import com.example.myvillage.combat.definition.MoveKind;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Optional;

/**
 * Draws the first-person 剑光 ribbon swept by the held weapon's blade during each move's strike
 * window. The blade is re-posed at earlier visual ticks, so the ribbon follows the exact same
 * curve as the held item without keeping any frame history, and it pauses with the hit-stop
 * because it reads the same visual tick as the viewmodel.
 *
 * <p>The ribbon is alpha-blended and tapers with age: the newest sample covers the outer 45% of
 * the blade, older samples shrink to a sliver at the tip, and a near-white edge band runs along
 * the tip's path. Thrust moves ({@link MoveKind#THRUST}) sweep no area, so they draw one
 * camera-facing streak along the blade.
 * The blade's base and tip come from the sword geometry contract ({@link SwordGeometry}), placed
 * through the same grip frame and sword scale as the held item.
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

    private FirstPersonSwordTrail() {
    }

    public static void onRenderHand(RenderHandEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null
                || event.getHand() != InteractionHand.MAIN_HAND
                || player.isInvisible()
                || ClientCombatState.mode() != CombatMode.CULTIVATION) {
            return;
        }
        Optional<FirstPersonSwingResources.WeaponRig> rig = FirstPersonSwingResources.forStack(event.getItemStack());
        Optional<FirstPersonWeaponAnimator.Frame> frame = rig.isEmpty()
                ? Optional.empty()
                : FirstPersonWeaponAnimator.INSTANCE.currentFrame(player, event.getPartialTick());
        if (rig.isEmpty() || frame.isEmpty()) {
            return;
        }
        Optional<FirstPersonSwing> swing = rig.map(FirstPersonSwingResources.WeaponRig::swing);
        FirstPersonSwing.Move move = swing.get().move(frame.get().moveIndex());
        float now = frame.get().tick();
        HumanoidArm arm = player.getMainArm();
        VertexConsumer consumer = event.getMultiBufferSource().getBuffer(CombatRenderTypes.SWORD_TRAIL_TRANSLUCENT);

        boolean thrust = SwordTrailShape.streak(rig.get().style().move(frame.get().moveIndex()).kind());
        if (thrust) {
            float alpha = SwordTrailShape.streakAlpha(now, move.strikeStartTick(), STREAK_TICKS);
            if (alpha <= 0.0F) {
                return;
            }
            Vector3f[] blade = bladePoints(
                    event.getPoseStack(), arm, event.getEquipProgress(), swing.get(),
                    move.sample(Math.min(now, move.strikeEndTick())));
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
                    move.sample(tick));
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

    /** Blade base and tip for one pose, in the event pose stack's space. */
    static Vector3f[] bladePoints(
            PoseStack poseStack,
            HumanoidArm arm,
            float equipProgress,
            FirstPersonSwing swing,
            FirstPersonSwing.Pose pose) {
        Matrix4f matrix = new Matrix4f(poseStack.last().pose());
        SwordGeometry sword = swing.sword();
        return new Vector3f[] {
                matrix.transformPosition(FirstPersonSwordTransform.swordPoint(
                        arm, equipProgress, swing, pose, sword.bladeBase())),
                matrix.transformPosition(FirstPersonSwordTransform.swordPoint(
                        arm, equipProgress, swing, pose, sword.bladeTip()))
        };
    }
}
