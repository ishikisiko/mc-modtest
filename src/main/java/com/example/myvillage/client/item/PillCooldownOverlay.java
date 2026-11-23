package com.example.myvillage.client.item;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;

public final class PillCooldownOverlay {
    private static final int SHADE = 0xA00C0C10;
    private static final int HALF = 8;
    private static final int DEPTH = 170;
    private static final float[] CORNER_FRACTIONS = {.125F, .375F, .625F, .875F, 1F};
    private static final int[] CORNER_X = {HALF, HALF, -HALF, -HALF, 0};
    private static final int[] CORNER_Y = {-HALF, HALF, HALF, -HALF, -HALF};

    private PillCooldownOverlay() {}

    /** Darkens the remaining clockwise arc, clipped to the item's 16 by 16 slot. */
    public static void render(GuiGraphics graphics, int x, int y, float remaining) {
        if (remaining >= 1) {
            graphics.fill(RenderType.guiOverlay(), x, y, x + 16, y + 16, DEPTH, SHADE);
            return;
        }
        double elapsed = 1.0 - remaining;
        double angle = elapsed * Math.TAU;
        double dx = Math.sin(angle), dy = -Math.cos(angle);
        double extent = HALF / Math.max(Math.abs(dx), Math.abs(dy));
        float startX = (float) (extent * dx), startY = (float) (extent * dy);
        int centerX = x + HALF, centerY = y + HALF;
        Matrix4f transform = graphics.pose().last().pose();
        VertexConsumer vertices = graphics.bufferSource().getBuffer(RenderType.guiOverlay());
        for (int corner = 0; corner < CORNER_FRACTIONS.length; corner++) {
            if (elapsed >= CORNER_FRACTIONS[corner]) continue;
            int endX = CORNER_X[corner], endY = CORNER_Y[corner];
            vertices.addVertex(transform, centerX, centerY, DEPTH).setColor(SHADE);
            vertices.addVertex(transform, centerX + endX, centerY + endY, DEPTH).setColor(SHADE);
            vertices.addVertex(transform, centerX + startX, centerY + startY, DEPTH).setColor(SHADE);
            vertices.addVertex(transform, centerX + startX, centerY + startY, DEPTH).setColor(SHADE);
            startX = endX;
            startY = endY;
        }
        graphics.flush();
    }
}
