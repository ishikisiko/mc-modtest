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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

import java.util.Optional;

public final class QingfengFirstPersonAnimator implements IClientItemExtensions {
    public static final QingfengFirstPersonAnimator INSTANCE = new QingfengFirstPersonAnimator();

    private static final float BLEND_OUT_TICKS = 3.0F;
    private static final float HIT_STOP_SHAKE = 0.012F;

    private int activeMoveIndex = -1;
    private double actionStartTick;
    private SwingClock clock;
    private boolean swingSoundPlayed;
    private FirstPersonSwing.Pose blendFrom;
    private double blendStartTick;
    private Frame probeFrame;

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
        double startTick = player.level().getGameTime() - Math.max(0.0F, elapsedTicks);
        if (animator.activeMoveIndex == moveIndex && Math.abs(animator.actionStartTick - startTick) < 1.0E-3) {
            return;
        }
        // An authoritative correction of the same predicted move keeps its hit-stop and sound state.
        boolean sameMove = animator.activeMoveIndex == moveIndex;
        animator.activeMoveIndex = moveIndex;
        animator.actionStartTick = startTick;
        animator.blendFrom = null;
        if (!sameMove) {
            animator.clock = new SwingClock(BasicSwordStyle.DEFINITION.move(moveIndex).totalTicks());
            animator.swingSoundPlayed = false;
        }
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

    /** Presentation-only hit-stop after the server confirmed damage for the local action. */
    static void confirmHit(LocalPlayer player) {
        QingfengFirstPersonAnimator animator = INSTANCE;
        if (animator.activeMoveIndex < 0 || animator.clock == null) {
            return;
        }
        animator.clock.beginHitStop((float) animator.realTick(player, 0.0F));
    }

    static void clientTick(LocalPlayer player) {
        QingfengFirstPersonAnimator animator = INSTANCE;
        if (animator.activeMoveIndex < 0 || animator.swingSoundPlayed || animator.clock == null) {
            return;
        }
        AttackMoveDefinition move = BasicSwordStyle.DEFINITION.move(animator.activeMoveIndex);
        float visualTick = animator.clock.visualTick((float) animator.realTick(player, 0.0F));
        if (visualTick + 0.5F < move.activeStartTick()) {
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
                feedback.swingPitch(),
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
                poseStack, arm, equipProcess, swing.get().rig(), currentPose(player, partialTick, swing.get()));
        return true;
    }

    FirstPersonSwing.Pose currentPose(LocalPlayer player, float partialTick, FirstPersonSwing swing) {
        Optional<Frame> frame = currentFrame(player, partialTick);
        if (frame.isPresent()) {
            FirstPersonSwing.Pose pose = swing.sample(frame.get().moveIndex(), frame.get().tick());
            return frame.get().hitStop() ? shaken(pose, player, partialTick) : pose;
        }
        if (blendFrom != null) {
            double elapsed = player.level().getGameTime() + partialTick - blendStartTick;
            if (elapsed >= 0.0 && elapsed < BLEND_OUT_TICKS) {
                float progress = FirstPersonSwing.Ease.IN_OUT.apply((float) (elapsed / BLEND_OUT_TICKS));
                return FirstPersonSwing.Pose.interpolate(blendFrom, swing.neutral(), progress);
            }
            blendFrom = null;
        }
        return swing.neutral();
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
        return player.level().getGameTime() + partialTick - actionStartTick;
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
        blendFrom = swing.get().sample(activeMoveIndex, clock.visualTick((float) realTick));
        blendStartTick = player.level().getGameTime();
    }

    private static FirstPersonSwing.Pose shaken(
            FirstPersonSwing.Pose pose,
            LocalPlayer player,
            float partialTick) {
        double time = (player.level().getGameTime() + partialTick) * 2.3;
        float dx = (float) Math.sin(time * 7.0) * HIT_STOP_SHAKE;
        float dy = (float) Math.cos(time * 9.0) * HIT_STOP_SHAKE;
        return new FirstPersonSwing.Pose(
                pose.plane(), pose.sweep(), pose.reach(), pose.lead(), pose.lift(), pose.twist(),
                pose.x() + dx, pose.y() + dy, pose.z());
    }

    private void clear() {
        activeMoveIndex = -1;
        actionStartTick = 0.0;
        clock = null;
        swingSoundPlayed = false;
    }

    record Frame(int moveIndex, float tick, boolean hitStop) {
    }
}
