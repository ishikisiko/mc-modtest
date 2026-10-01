package com.example.myvillage.client.combat;

import com.example.myvillage.combat.CombatMode;
import com.example.myvillage.combat.CombatSounds;
import com.example.myvillage.combat.definition.AttackMoveDefinition;
import com.example.myvillage.combat.definition.BasicSwordStyle;
import com.example.myvillage.combat.definition.MoveFeedback;
import com.example.myvillage.item.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Optional;

public final class QingfengFirstPersonAnimator implements IClientItemExtensions {
    public static final QingfengFirstPersonAnimator INSTANCE = new QingfengFirstPersonAnimator();

    private static final float BLEND_OUT_TICKS = 3.0F;
    /** A chained move cross-fades from the pose on screen instead of snapping. */
    static final float CHAIN_BLEND_TICKS = 2.0F;
    /** Authoritative start corrections up to this size are slewed instead of snapped. */
    static final float RESYNC_SLEW_LIMIT_TICKS = 2.0F;
    static final float RESYNC_SLEW_TICKS = 3.0F;
    /** Hit-stop shake amplitude in rig units: base plus a share per stop tick, decaying over the stop. */
    static final float HIT_STOP_SHAKE = 0.018F;
    static final float HIT_STOP_SHAKE_PER_TICK = 0.003F;
    /** About 12 Hz: slow enough to read at 60 fps as a shudder rather than noise. */
    private static final float HIT_STOP_SHAKE_RADIANS_PER_TICK = 3.8F;
    private static final float STRIKE_DIRECTION_PROBE_TICKS = 0.35F;
    /** Idle breathing on the neutral hold: rig units / degrees, one breath about every 3.5 s. */
    static final float BREATH_RISE = 0.006F;
    static final float BREATH_LIFT_DEGREES = 1.2F;
    static final float BREATH_ELBOW_DEGREES = 2.5F;
    static final float BREATH_PERIOD_TICKS = 70.0F;
    /** Breathing fades back in over this many ticks after a move, so it never pops. */
    static final float BREATH_FADE_TICKS = 12.0F;

    private int activeMoveIndex = -1;
    private double actionStartTick;
    private double slewOffset;
    private double slewStartTick;
    private SwingClock clock;
    private boolean swingSoundPlayed;
    private FirstPersonSwing.Pose blendFrom;
    private double blendStartTick;
    private float blendTicks = BLEND_OUT_TICKS;
    private Frame probeFrame;
    private double idleSinceTick = Double.NEGATIVE_INFINITY;
    private final Vector3f shakeDirection = new Vector3f(1.0F, 0.0F, 0.0F);

    private QingfengFirstPersonAnimator() {
    }

    static void play(AbstractClientPlayer player, ResourceLocation animationId, float elapsedTicks) {
        LocalPlayer localPlayer = Minecraft.getInstance().player;
        if (player != localPlayer) {
            return;
        }
        int moveIndex = BasicSwordStyle.DEFINITION.indexOf(animationId);
        if (moveIndex < 0) {
            return;
        }
        QingfengFirstPersonAnimator animator = INSTANCE;
        double now = player.level().getGameTime();
        double startTick = player.level().getGameTime() - Math.max(0.0F, elapsedTicks);
        if (animator.activeMoveIndex == moveIndex && animator.clock != null) {
            // An authoritative correction of the same predicted move keeps its hit-stop and sound
            // state. Small corrections are slewed so the blade never teleports mid-strike.
            double shift = animator.effectiveStartTick(now) - startTick;
            if (Math.abs(shift) < 1.0E-3) {
                return;
            }
            animator.actionStartTick = startTick;
            if (Math.abs(shift) <= RESYNC_SLEW_LIMIT_TICKS) {
                animator.slewOffset = shift;
                animator.slewStartTick = now;
            } else {
                animator.slewOffset = 0.0;
            }
            return;
        }

        // A different move (a chained combo step, or a start right after a stop) cross-fades from
        // whatever is on screen now.
        Optional<FirstPersonSwing> swing = FirstPersonSwingResources.current();
        FirstPersonSwing.Pose from = null;
        if (swing.isPresent() && (animator.activeMoveIndex >= 0 || animator.blendFrom != null)) {
            from = animator.currentPose(localPlayer, 0.0F, swing.get());
        }
        animator.activeMoveIndex = moveIndex;
        animator.idleSinceTick = Double.NEGATIVE_INFINITY;
        animator.actionStartTick = startTick;
        animator.slewOffset = 0.0;
        animator.clock = new SwingClock(BasicSwordStyle.DEFINITION.move(moveIndex).totalTicks());
        animator.swingSoundPlayed = false;
        animator.blendFrom = from;
        animator.blendStartTick = now;
        animator.blendTicks = CHAIN_BLEND_TICKS;
    }

    static void stop(AbstractClientPlayer player) {
        if (player == Minecraft.getInstance().player) {
            INSTANCE.beginBlendOut(player);
            INSTANCE.clear();
        }
    }

    /** Development probe: holds one visual frame until released. Sends nothing to the server. */
    static void probe(int moveIndex, float tick) {
        INSTANCE.probeFrame = new Frame(moveIndex, tick, false);
    }

    static void releaseProbe() {
        INSTANCE.probeFrame = null;
    }

    /**
     * Presentation-only hit-stop after the server confirmed damage for the local action. The stop
     * lasts the move's {@code MoveFeedback.hitStopTicks} and starts when the drawn blade reaches its
     * contact tick, or at once when the confirmation arrives later than that.
     */
    static void confirmHit(LocalPlayer player) {
        QingfengFirstPersonAnimator animator = INSTANCE;
        if (animator.activeMoveIndex < 0 || animator.clock == null) {
            return;
        }
        MoveFeedback feedback = BasicSwordStyle.feedback(animator.activeMoveIndex);
        float now = (float) animator.realTick(player, 0.0F);
        float start = now;
        Optional<FirstPersonSwing> swing = FirstPersonSwingResources.current();
        if (swing.isPresent()) {
            FirstPersonSwing.Move move = swing.get().move(animator.activeMoveIndex);
            start = Math.max(now, animator.clock.realTickForVisual(move.contactTick()));
            animator.aimShake(swing.get(), move);
        }
        animator.clock.beginHitStop(start, feedback.hitStopTicks());
    }

    /**
     * Visual ticks advanced per real tick for this player's action right now: 0 in the frozen part
     * of a hit-stop, then the creep and catch-up rates; 1 when idle or for any other player.
     */
    static float visualRate(AbstractClientPlayer player) {
        QingfengFirstPersonAnimator animator = INSTANCE;
        Minecraft minecraft = Minecraft.getInstance();
        if (!(player instanceof LocalPlayer localPlayer)
                || player != minecraft.player
                || animator.activeMoveIndex < 0
                || animator.clock == null) {
            return 1.0F;
        }
        float partialTick = minecraft.getTimer().getGameTimeDeltaPartialTick(true);
        return animator.clock.rate((float) animator.realTick(localPlayer, partialTick));
    }

    static void clientTick(LocalPlayer player) {
        QingfengFirstPersonAnimator animator = INSTANCE;
        if (animator.activeMoveIndex < 0 || animator.swingSoundPlayed || animator.clock == null) {
            return;
        }
        AttackMoveDefinition move = BasicSwordStyle.DEFINITION.move(animator.activeMoveIndex);
        float visualTick = animator.clock.visualTick((float) animator.realTick(player, 0.0F));
        // The whoosh leads the blade by about one tick, like the server swing sound.
        if (visualTick + 1.5F < move.activeStartTick()) {
            return;
        }
        animator.swingSoundPlayed = true;
        MoveFeedback feedback = BasicSwordStyle.feedback(animator.activeMoveIndex);
        player.level().playLocalSound(
                player.getX(),
                player.getY(),
                player.getZ(),
                feedback.swingSound() == MoveFeedback.SwingSound.THRUST
                        ? CombatSounds.SWORD_THRUST.get()
                        : CombatSounds.SWORD_CUT.get(),
                SoundSource.PLAYERS,
                0.9F,
                CombatSounds.jitteredSwingPitch(feedback.swingPitch(), player.getRandom()),
                false);
    }

    @Override
    public boolean applyForgeHandTransform(
            PoseStack poseStack,
            LocalPlayer player,
            HumanoidArm arm,
            ItemStack itemInHand,
            float partialTick,
            float equipProcess,
            float swingProcess) {
        if (arm != player.getMainArm()
                || !itemInHand.is(ModItems.QINGFENG_SWORD.get())
                || ClientCombatState.mode() != CombatMode.CULTIVATION) {
            return false;
        }
        Optional<FirstPersonSwing> swing = FirstPersonSwingResources.current();
        if (swing.isEmpty()) {
            return false;
        }
        FirstPersonSwordTransform.apply(
                poseStack, arm, equipProcess, swing.get(), currentPose(player, partialTick, swing.get()),
                displayTransform(player, itemInHand, arm));
        return true;
    }

    /**
     * The baked model's own first-person display transform for this hand, exactly as the item pass
     * will apply it next (through {@code BakedModel#applyTransform}, so wrapper models such as
     * separate-transforms forward to the model actually drawn).
     */
    private static Matrix4f displayTransform(LocalPlayer player, ItemStack stack, HumanoidArm arm) {
        boolean leftHand = arm == HumanoidArm.LEFT;
        ItemDisplayContext context = leftHand
                ? ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                : ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
        BakedModel model = Minecraft.getInstance().getItemRenderer()
                .getModel(stack, player.level(), player, player.getId() + context.ordinal());
        PoseStack scratch = new PoseStack();
        model.applyTransform(context, scratch, leftHand);
        return new Matrix4f(scratch.last().pose());
    }

    /**
     * The one pose drawn this frame for the Qingfeng viewmodel: the sword item transform and the
     * first-person arm both read it, so the hand never leaves the handle. Empty when the rig does
     * not apply (not in cultivation mode, Qingfeng not in the main hand, or no rig loaded).
     */
    static Optional<FirstPersonSwing.Pose> displayedPose(LocalPlayer player, float partialTick) {
        if (ClientCombatState.mode() != CombatMode.CULTIVATION
                || !player.getMainHandItem().is(ModItems.QINGFENG_SWORD.get())) {
            return Optional.empty();
        }
        return FirstPersonSwingResources.current()
                .map(swing -> INSTANCE.currentPose(player, partialTick, swing));
    }

    FirstPersonSwing.Pose currentPose(LocalPlayer player, float partialTick, FirstPersonSwing swing) {
        Optional<Frame> frame = currentFrame(player, partialTick);
        FirstPersonSwing.Pose target = swing.neutral();
        if (frame.isEmpty() && probeFrame == null) {
            target = breathing(target, player.level().getGameTime() + partialTick);
        }
        if (frame.isPresent()) {
            target = swing.sample(frame.get().moveIndex(), frame.get().tick());
            if (frame.get().hitStop()) {
                target = shaken(target, (float) realTick(player, partialTick));
            }
        }
        if (blendFrom != null) {
            double elapsed = player.level().getGameTime() + partialTick - blendStartTick;
            if (elapsed >= 0.0 && elapsed < blendTicks) {
                float progress = FirstPersonSwing.Ease.IN_OUT.apply((float) (elapsed / blendTicks));
                return FirstPersonSwing.Pose.interpolate(blendFrom, target, progress);
            }
            if (elapsed >= blendTicks) {
                blendFrom = null;
            }
        }
        return target;
    }

    Optional<Frame> currentFrame(LocalPlayer player, float partialTick) {
        if (probeFrame != null) {
            return Optional.of(probeFrame);
        }
        if (activeMoveIndex < 0 || clock == null) {
            return Optional.empty();
        }
        if (ClientCombatState.mode() != CombatMode.CULTIVATION
                || !player.getMainHandItem().is(ModItems.QINGFENG_SWORD.get())) {
            clear();
            blendFrom = null;
            return Optional.empty();
        }

        int totalTicks = BasicSwordStyle.DEFINITION.move(activeMoveIndex).totalTicks();
        double realTick = realTick(player, partialTick);
        if (realTick < 0.0) {
            return Optional.empty();
        }
        if (realTick >= totalTicks) {
            clear();
            return Optional.empty();
        }
        return Optional.of(new Frame(
                activeMoveIndex,
                clock.visualTick((float) realTick),
                clock.inHitStop((float) realTick)));
    }

    private double realTick(LocalPlayer player, float partialTick) {
        double now = player.level().getGameTime() + partialTick;
        return now - effectiveStartTick(now);
    }

    /** The action start including any in-progress slew toward an authoritative correction. */
    private double effectiveStartTick(double now) {
        if (slewOffset == 0.0) {
            return actionStartTick;
        }
        double remaining = 1.0 - (now - slewStartTick) / RESYNC_SLEW_TICKS;
        if (remaining <= 0.0) {
            slewOffset = 0.0;
            return actionStartTick;
        }
        return actionStartTick + slewOffset * Math.min(1.0, remaining);
    }

    private void beginBlendOut(AbstractClientPlayer player) {
        Optional<FirstPersonSwing> swing = FirstPersonSwingResources.current();
        if (activeMoveIndex < 0 || clock == null || swing.isEmpty()
                || !(player instanceof LocalPlayer localPlayer)) {
            return;
        }
        double realTick = realTick(localPlayer, 0.0F);
        if (realTick < 0.0 || realTick >= swing.get().move(activeMoveIndex).totalTicks()) {
            return;
        }
        blendFrom = currentPose(localPlayer, 0.0F, swing.get());
        blendStartTick = player.level().getGameTime();
        blendTicks = BLEND_OUT_TICKS;
    }

    /** Points the hit-stop shudder along the blade's travel at the move's contact tick. */
    private void aimShake(FirstPersonSwing swing, FirstPersonSwing.Move move) {
        float contact = move.contactTick();
        Vector3f before = gripPoint(swing, move.sample(contact - STRIKE_DIRECTION_PROBE_TICKS));
        Vector3f after = gripPoint(swing, move.sample(contact + STRIKE_DIRECTION_PROBE_TICKS));
        Vector3f direction = after.sub(before);
        if (direction.lengthSquared() > 1.0E-8F) {
            shakeDirection.set(direction.normalize());
        } else {
            shakeDirection.set(1.0F, 0.0F, 0.0F);
        }
    }

    /** Grip position in right-hand rig space, where pose offsets are authored. */
    private static Vector3f gripPoint(FirstPersonSwing swing, FirstPersonSwing.Pose pose) {
        return FirstPersonSwordTransform.gripFrame(HumanoidArm.RIGHT, 0.0F, swing.rig(), pose)
                .getTranslation(new Vector3f());
    }

    /**
     * Hit-stop shudder: the blade first bites forward along its strike, then recoils, with a
     * smaller cross-axis tremor. Amplitude grows with the stop length and decays to zero by its end.
     */
    private FirstPersonSwing.Pose shaken(FirstPersonSwing.Pose pose, float realTick) {
        if (clock == null || !clock.hasHitStop()) {
            return pose;
        }
        float stopTicks = clock.hitStopTicks();
        float elapsed = Math.max(0.0F, realTick - clock.hitStopStart());
        float decay = 1.0F - clock.hitStopProgress(realTick);
        float amplitude = (HIT_STOP_SHAKE + HIT_STOP_SHAKE_PER_TICK * stopTicks) * decay;
        float along = (float) Math.cos(elapsed * HIT_STOP_SHAKE_RADIANS_PER_TICK) * amplitude;
        float across = (float) Math.sin(elapsed * HIT_STOP_SHAKE_RADIANS_PER_TICK * 1.7F) * amplitude * 0.35F;
        // Cross axis: the strike direction turned 90 degrees in the view plane.
        float crossX = -shakeDirection.y;
        float crossY = shakeDirection.x;
        float crossLength = (float) Math.sqrt(crossX * crossX + crossY * crossY);
        if (crossLength > 1.0E-4F) {
            crossX /= crossLength;
            crossY /= crossLength;
        }
        return new FirstPersonSwing.Pose(
                pose.plane(), pose.sweep(), pose.reach(), pose.lead(), pose.lift(), pose.twist(),
                pose.x() + shakeDirection.x * along + crossX * across,
                pose.y() + shakeDirection.y * along + crossY * across,
                pose.z() + shakeDirection.z * along,
                pose.gripRoll(),
                pose.elbow());
    }

    /**
     * A slow breath on the neutral hold: the sword rises and tips back a little and the elbow
     * lifts with it. It ramps in after a move so the hold never jumps.
     */
    private FirstPersonSwing.Pose breathing(FirstPersonSwing.Pose pose, double now) {
        if (idleSinceTick == Double.NEGATIVE_INFINITY) {
            idleSinceTick = now;
        }
        float fade = (float) Math.min(1.0, Math.max(0.0, (now - idleSinceTick) / BREATH_FADE_TICKS));
        float breath = fade * (float) Math.sin(now * 2.0 * Math.PI / BREATH_PERIOD_TICKS);
        return new FirstPersonSwing.Pose(
                pose.plane(), pose.sweep(), pose.reach(), pose.lead(),
                pose.lift() + BREATH_LIFT_DEGREES * breath,
                pose.twist(),
                pose.x(),
                pose.y() + BREATH_RISE * breath,
                pose.z(),
                pose.gripRoll(),
                pose.elbow() + BREATH_ELBOW_DEGREES * breath);
    }

    private void clear() {
        activeMoveIndex = -1;
        actionStartTick = 0.0;
        slewOffset = 0.0;
        clock = null;
        swingSoundPlayed = false;
    }

    record Frame(int moveIndex, float tick, boolean hitStop) {
    }
}
