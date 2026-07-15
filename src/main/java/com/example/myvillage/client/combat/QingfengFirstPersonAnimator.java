package com.example.myvillage.client.combat;

import com.example.myvillage.combat.CombatMode;
import com.example.myvillage.combat.definition.BasicSwordStyle;
import com.example.myvillage.item.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

import java.util.Optional;

public final class QingfengFirstPersonAnimator implements IClientItemExtensions {
    public static final QingfengFirstPersonAnimator INSTANCE = new QingfengFirstPersonAnimator();

    private int activeMoveIndex = -1;
    private double actionStartTick;

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
        INSTANCE.activeMoveIndex = moveIndex;
        INSTANCE.actionStartTick = player.level().getGameTime() - Math.max(0.0F, elapsedTicks);
    }

    static void stop(AbstractClientPlayer player) {
        if (player == Minecraft.getInstance().player) {
            INSTANCE.clear();
        }
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
        if (arm != player.getMainArm() || !itemInHand.is(ModItems.QINGFENG_SWORD.get())) {
            return false;
        }

        if (ClientCombatState.mode() != CombatMode.CULTIVATION) {
            return false;
        }

        FirstPersonSwordPose.Pose pose = currentFrame(player, partialTick)
                .map(Frame::pose)
                .orElse(FirstPersonSwordPose.neutral());
        FirstPersonSwordTransform.apply(poseStack, arm, equipProcess, pose);
        return true;
    }

    Optional<Frame> currentFrame(LocalPlayer player, float partialTick) {
        if (activeMoveIndex < 0) {
            return Optional.empty();
        }
        if (ClientCombatState.mode() != CombatMode.CULTIVATION
                || !player.getMainHandItem().is(ModItems.QINGFENG_SWORD.get())) {
            clear();
            return Optional.empty();
        }

        int moveIndex = activeMoveIndex;
        int totalTicks = BasicSwordStyle.DEFINITION.move(moveIndex).totalTicks();
        double elapsedTicks = player.level().getGameTime() + partialTick - actionStartTick;
        if (elapsedTicks < 0.0) {
            return Optional.empty();
        }
        if (elapsedTicks >= totalTicks) {
            clear();
            return Optional.empty();
        }

        float progress = (float) (elapsedTicks / totalTicks);
        return Optional.of(new Frame(
                moveIndex,
                progress,
                FirstPersonSwordPose.sample(moveIndex, progress)));
    }

    private void clear() {
        activeMoveIndex = -1;
        actionStartTick = 0.0;
    }

    record Frame(int moveIndex, float progress, FirstPersonSwordPose.Pose pose) {
    }
}
