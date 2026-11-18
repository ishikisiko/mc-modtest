package com.example.myvillage.client.forging;

import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;

/** The station window renders the same baked model used in the world. */
final class StationPanel {
    private StationPanel() {}

    static void paper(GuiGraphics g, int x, int y, int width, int height) {
        g.fill(x, y, x + width, y + height, 0xff293935);
        g.renderOutline(x + 2, y + 2, width - 4, height - 4, 0xffa78b57);
        g.fill(x + 4, y + 4, x + width - 4, y + height - 4, 0xffe5dbc1);
        g.fill(x + 10, y + 22, x + width - 10, y + 23, 0xffb39b69);
    }

    static void slot(GuiGraphics g, int x, int y) {
        g.fill(x - 1, y - 1, x + 17, y + 17, 0xff978665);
        g.fill(x, y, x + 16, y + 16, 0xffc7baa0);
        g.fill(x, y + 15, x + 16, y + 16, 0xffeee2c4);
    }

    static void station(GuiGraphics g, int x, int y, int width, int height, BlockState state, float partial) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        g.fill(x, y, x + width, y + height, 0xff213433);
        g.renderOutline(x, y, width, height, 0xff83795f);
        g.enableScissor(x + 2, y + 2, x + width - 2, y + height - 2);
        var pose = g.pose();
        pose.pushPose();
        float size = Math.min(width, height) * .57f;
        pose.translate(x + width / 2f, y + height / 2f, 88);
        pose.scale(size, -size, size);
        pose.mulPose(Axis.XP.rotationDegrees(24));
        float spin = ((minecraft.level.getGameTime() % 900 + partial) * .4f + 25);
        pose.mulPose(Axis.YP.rotationDegrees(spin));
        pose.translate(-.5, -.5, -.5);
        minecraft.getBlockRenderer().renderSingleBlock(state, pose, g.bufferSource(), LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
        g.flush();
        pose.popPose();
        g.disableScissor();
    }
}
