package com.example.myvillage.client.cultivation.panel;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

import java.util.Arrays;

/**
 * Soft-edged vector drawing for the panel: strokes, discs, and arcs at fractional GUI
 * coordinates with feathered edges, batched into one triangle list per {@link #begin} /
 * {@link #end}. {@code GuiGraphics} only fills whole-pixel rectangles; the meridian diagram
 * needs diagonal and curved channels and glow, so it draws through this instead.
 *
 * <p>A brush keeps its own vertices until {@link #end}. Every {@code Tesselator.begin} writes
 * into one shared buffer, so two batches built there at the same time would mix their vertices;
 * collecting here lets several brushes stay open side by side.
 */
final class VectorBrush {
    private static final int CIRCLE_SEGMENTS = 28;

    private final Matrix4f pose;
    /** x, y per vertex. */
    private float[] positions = new float[1536];
    /** ARGB per vertex. */
    private int[] colors = new int[768];
    private int vertices;

    VectorBrush(GuiGraphics graphics) {
        this.pose = graphics.pose().last().pose();
    }

    /** Starts a batch; nothing is drawn until {@link #end}. */
    VectorBrush begin() {
        vertices = 0;
        return this;
    }

    /** Draws the batch: {@code additive} adds light (for glow), otherwise colours blend over. */
    void end(boolean additive) {
        if (vertices == 0) {
            return;
        }
        BufferBuilder buffer = Tesselator.getInstance()
                .begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        for (int index = 0; index < vertices; index++) {
            int color = colors[index];
            buffer.addVertex(pose, positions[index * 2], positions[index * 2 + 1], 0.0F)
                    .setColor((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, color >>> 24);
        }
        vertices = 0;
        MeshData mesh = buffer.buildOrThrow();
        RenderSystem.enableBlend();
        if (additive) {
            RenderSystem.blendFuncSeparate(
                    GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE,
                    GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE);
        } else {
            RenderSystem.defaultBlendFunc();
        }
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferUploader.drawWithShader(mesh);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private void vertex(float x, float y, int color, float alpha) {
        if (vertices == colors.length) {
            positions = Arrays.copyOf(positions, positions.length * 2);
            colors = Arrays.copyOf(colors, colors.length * 2);
        }
        int a = Math.max(0, Math.min(255, Math.round(((color >>> 24) & 0xFF) * alpha)));
        positions[vertices * 2] = x;
        positions[vertices * 2 + 1] = y;
        colors[vertices] = a << 24 | (color & 0xFFFFFF);
        vertices++;
    }

    private void triangle(float x0, float y0, float a0, float x1, float y1, float a1,
                          float x2, float y2, float a2, int color) {
        vertex(x0, y0, color, a0);
        vertex(x1, y1, color, a1);
        vertex(x2, y2, color, a2);
    }

    /** A quad with per-corner alpha, corners in order around the edge. */
    private void quad(float x0, float y0, float a0, float x1, float y1, float a1,
                      float x2, float y2, float a2, float x3, float y3, float a3, int color) {
        triangle(x0, y0, a0, x1, y1, a1, x2, y2, a2, color);
        triangle(x0, y0, a0, x2, y2, a2, x3, y3, a3, color);
    }

    /** An axis-aligned rectangle at fractional coordinates. */
    VectorBrush rect(float x0, float y0, float x1, float y1, int color, float alpha) {
        quad(x0, y0, alpha, x1, y0, alpha, x1, y1, alpha, x0, y1, alpha, color);
        return this;
    }

    /**
     * A polyline {@code width} wide with edges fading out over {@code feather}. {@code alphas}
     * (one per point) shades it along its length; null draws it evenly at {@code alpha}.
     */
    VectorBrush stroke(float[] xs, float[] ys, int from, int to, float width, float feather,
                       int color, float alpha, float[] alphas) {
        if (to - from < 1) {
            return this;
        }
        int count = to - from + 1;
        float[] nx = new float[count];
        float[] ny = new float[count];
        for (int index = 0; index < count; index++) {
            int point = from + index;
            int before = Math.max(from, point - 1);
            int after = Math.min(to, point + 1);
            float dx = xs[after] - xs[before];
            float dy = ys[after] - ys[before];
            float length = (float) Math.hypot(dx, dy);
            if (length == 0.0F) {
                nx[index] = index > 0 ? nx[index - 1] : 0.0F;
                ny[index] = index > 0 ? ny[index - 1] : 1.0F;
            } else {
                nx[index] = -dy / length;
                ny[index] = dx / length;
            }
        }
        float half = width / 2.0F;
        float outer = half + feather;
        for (int index = 0; index < count - 1; index++) {
            int p = from + index;
            int q = p + 1;
            float ap = alpha * (alphas == null ? 1.0F : alphas[p]);
            float aq = alpha * (alphas == null ? 1.0F : alphas[q]);
            float pnx = nx[index];
            float pny = ny[index];
            float qnx = nx[index + 1];
            float qny = ny[index + 1];
            // core
            quad(xs[p] - pnx * half, ys[p] - pny * half, ap,
                    xs[q] - qnx * half, ys[q] - qny * half, aq,
                    xs[q] + qnx * half, ys[q] + qny * half, aq,
                    xs[p] + pnx * half, ys[p] + pny * half, ap, color);
            if (feather > 0.0F) {
                quad(xs[p] + pnx * half, ys[p] + pny * half, ap,
                        xs[q] + qnx * half, ys[q] + qny * half, aq,
                        xs[q] + qnx * outer, ys[q] + qny * outer, 0.0F,
                        xs[p] + pnx * outer, ys[p] + pny * outer, 0.0F, color);
                quad(xs[p] - pnx * half, ys[p] - pny * half, ap,
                        xs[q] - qnx * half, ys[q] - qny * half, aq,
                        xs[q] - qnx * outer, ys[q] - qny * outer, 0.0F,
                        xs[p] - pnx * outer, ys[p] - pny * outer, 0.0F, color);
            }
        }
        return this;
    }

    /** A straight stroke between two points. */
    VectorBrush line(float x0, float y0, float x1, float y1, float width, float feather, int color, float alpha) {
        return stroke(new float[] {x0, x1}, new float[] {y0, y1}, 0, 1, width, feather, color, alpha, null);
    }

    /**
     * A disc of {@code radius} at {@code alpha} in the middle that falls to {@code edgeAlpha}
     * at its rim, plus a {@code feather} fade outside the rim; a glow is a disc with no core.
     */
    VectorBrush disc(float cx, float cy, float radius, float feather, int color, float alpha, float edgeAlpha) {
        float previousX = cx + radius;
        float previousY = cy;
        for (int segment = 1; segment <= CIRCLE_SEGMENTS; segment++) {
            double angle = 2.0D * Math.PI * segment / CIRCLE_SEGMENTS;
            float x = cx + (float) Math.cos(angle) * radius;
            float y = cy + (float) Math.sin(angle) * radius;
            triangle(cx, cy, alpha, previousX, previousY, edgeAlpha, x, y, edgeAlpha, color);
            if (feather > 0.0F) {
                float scale = (radius + feather) / radius;
                quad(previousX, previousY, edgeAlpha, x, y, edgeAlpha,
                        cx + (x - cx) * scale, cy + (y - cy) * scale, 0.0F,
                        cx + (previousX - cx) * scale, cy + (previousY - cy) * scale, 0.0F, color);
            }
            previousX = x;
            previousY = y;
        }
        return this;
    }

    /**
     * An arc of a ring centred on {@code radius}, starting at {@code start} radians (0 is
     * twelve o'clock) and running clockwise through {@code sweep} radians.
     */
    VectorBrush arc(float cx, float cy, float radius, float width, float feather,
                    double start, double sweep, int color, float alpha) {
        int segments = Math.max(2, (int) Math.ceil(CIRCLE_SEGMENTS * 2 * Math.abs(sweep) / (2.0D * Math.PI)));
        float[] xs = new float[segments + 1];
        float[] ys = new float[segments + 1];
        for (int index = 0; index <= segments; index++) {
            double angle = start + sweep * index / segments;
            xs[index] = cx + (float) Math.sin(angle) * radius;
            ys[index] = cy - (float) Math.cos(angle) * radius;
        }
        return stroke(xs, ys, 0, segments, width, feather, color, alpha, null);
    }

    /** Draws {@code texture} (whole) over the rectangle, multiplied by {@code tint}. */
    static void texture(GuiGraphics graphics, ResourceLocation texture,
                        float x0, float y0, float x1, float y1, int tint) {
        Matrix4f pose = graphics.pose().last().pose();
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        int r = (tint >> 16) & 0xFF;
        int g = (tint >> 8) & 0xFF;
        int b = tint & 0xFF;
        int a = (tint >>> 24) & 0xFF;
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        buffer.addVertex(pose, x0, y0, 0.0F).setUv(0.0F, 0.0F).setColor(r, g, b, a);
        buffer.addVertex(pose, x0, y1, 0.0F).setUv(0.0F, 1.0F).setColor(r, g, b, a);
        buffer.addVertex(pose, x1, y1, 0.0F).setUv(1.0F, 1.0F).setColor(r, g, b, a);
        buffer.addVertex(pose, x1, y0, 0.0F).setUv(1.0F, 0.0F).setColor(r, g, b, a);
        BufferUploader.drawWithShader(buffer.buildOrThrow());
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }
}
