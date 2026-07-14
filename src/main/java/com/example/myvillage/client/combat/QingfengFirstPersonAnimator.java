package com.example.myvillage.client.combat;

import com.example.myvillage.combat.CombatMode;
import com.example.myvillage.combat.definition.BasicSwordStyle;
import com.example.myvillage.item.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

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
        if (activeMoveIndex < 0
                || ClientCombatState.mode() != CombatMode.CULTIVATION
                || arm != player.getMainArm()
                || !itemInHand.is(ModItems.QINGFENG_SWORD.get())
                || !player.getMainHandItem().is(ModItems.QINGFENG_SWORD.get())) {
            return false;
        }

        int totalTicks = BasicSwordStyle.DEFINITION.move(activeMoveIndex).totalTicks();
        double elapsedTicks = player.level().getGameTime() + partialTick - actionStartTick;
        if (elapsedTicks < 0.0 || elapsedTicks >= totalTicks) {
            if (elapsedTicks >= totalTicks) {
                clear();
            }
            return false;
        }

        FirstPersonSwordPose.Pose pose = FirstPersonSwordPose.sample(
                activeMoveIndex, (float) (elapsedTicks / totalTicks));
        float side = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        poseStack.translate(
                side * (0.56F + pose.x()),
                -0.52F - equipProcess * 0.6F + pose.y(),
                -0.72F + pose.z());
        poseStack.mulPose(Axis.XP.rotationDegrees(pose.pitch()));
        poseStack.mulPose(Axis.YP.rotationDegrees(side * pose.yaw()));
        poseStack.mulPose(Axis.ZP.rotationDegrees(side * pose.roll()));
        poseStack.scale(pose.scale(), pose.scale(), pose.scale());
        return true;
    }

    private void clear() {
        activeMoveIndex = -1;
        actionStartTick = 0.0;
    }
}
