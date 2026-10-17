package com.example.myvillage.client.combat;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** The generated NPC grip supplies orientation; the weapon contract supplies grip and scale. */
public final class NpcHeldWeapon {
    private NpcHeldWeapon() {}
    public static void render(LivingEntity npc, PoseStack poses, MultiBufferSource buffers, int light, float modelScale) {
        var stack = npc.getMainHandItem();
        var rig = FirstPersonSwingResources.forStack(stack).orElse(null);
        if (rig == null || stack.isEmpty()) return;
        var renderer = Minecraft.getInstance().getItemRenderer();
        var context = ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
        var model = renderer.getModel(stack, npc.level(), npc, npc.getId());
        PoseStack scratch = new PoseStack();
        model.applyTransform(context, scratch, false);
        Matrix4f display = new Matrix4f(scratch.last().pose());
        float scale = display.getScale(new Vector3f()).y / modelScale;
        poses.mulPose(FirstPersonWeaponTransform.itemToGrip(rig.swing().weapon(), scale, display));
        renderer.render(stack, context, false, poses, buffers, light, OverlayTexture.NO_OVERLAY, model);
    }
}
