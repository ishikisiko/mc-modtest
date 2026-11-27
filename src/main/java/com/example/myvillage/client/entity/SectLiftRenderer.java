package com.example.myvillage.client.entity;

import com.example.myvillage.sect.SectLiftPlatform;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.Blocks;

public final class SectLiftRenderer extends EntityRenderer<SectLiftPlatform> {
    private final BlockRenderDispatcher blocks;

    public SectLiftRenderer(EntityRendererProvider.Context context) {
        super(context);
        blocks = context.getBlockRenderDispatcher();
    }

    @Override public void render(SectLiftPlatform deck, float yaw, float partialTick, PoseStack pose,
                                 MultiBufferSource buffers, int light) {
        int width = deck.width();
        var rim = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath(
            "yuanxian", "zhuji_baiqing_lingyun_block")).defaultBlockState();
        var tread = Blocks.POLISHED_ANDESITE.defaultBlockState();
        pose.pushPose();
        pose.translate(-width*.5, 0, -width*.5);
        for (int z = 0; z < width; z++) for (int x = 0; x < width; x++) {
            pose.pushPose();
            pose.translate(x, 0, z);
            pose.scale(1F, SectLiftPlatform.THICKNESS, 1F);
            blocks.renderSingleBlock(x == 1 && z == 1 && width == 3 ? tread : rim,
                pose, buffers, light, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
        pose.popPose();
        super.render(deck, yaw, partialTick, pose, buffers, light);
    }

    @Override public ResourceLocation getTextureLocation(SectLiftPlatform deck) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
