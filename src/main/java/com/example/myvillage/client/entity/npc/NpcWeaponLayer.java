package com.example.myvillage.client.entity.npc;

import com.example.myvillage.client.combat.NpcHeldWeapon;
import com.example.myvillage.entity.npc.NpcEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

public final class NpcWeaponLayer<T extends NpcEntity> extends RenderLayer<T, NpcModel<T>> {
    public NpcWeaponLayer(RenderLayerParent<T, NpcModel<T>> parent) { super(parent); }
    @Override public void render(PoseStack poses, MultiBufferSource buffers, int light, T npc,
            float limbSwing, float limbSwingAmount, float partialTick, float age, float headYaw, float headPitch) {
        if (npc.isInvisible() || npc.getMainHandItem().isEmpty()) return;
        poses.pushPose();
        var model = getParentModel();
        model.applyGrip(poses, npc.combatEngaged() || npc.combatState().contains("move") ? "grip_right" : "sword_stowed");
        NpcHeldWeapon.render(npc, poses, buffers, light, model.modelScale());
        poses.popPose();
    }
}
