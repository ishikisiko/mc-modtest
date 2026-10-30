package com.example.myvillage.client.decoration;

import com.example.myvillage.decoration.SwordQiAppearance;
import com.example.myvillage.decoration.SwordQiPedestalBlock;
import com.example.myvillage.decoration.SwordQiPedestalEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;

/** Continuous faceted light, feathered halos and two smooth streams; no entity/particle spawning. */
public final class SwordQiPedestalRenderer implements BlockEntityRenderer<SwordQiPedestalEntity> {
    private static final ResourceLocation GLOW = ResourceLocation.fromNamespaceAndPath(
            "myvillage", "textures/block/sword_qi_glow.png");
    private static final ResourceLocation MOTE = ResourceLocation.fromNamespaceAndPath(
            "myvillage", "textures/block/sword_qi_mote.png");
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final double TAU = Math.PI * 2;

    public SwordQiPedestalRenderer(BlockEntityRendererProvider.Context context) { }

    @Override public void render(SwordQiPedestalEntity entity, float partialTick, PoseStack pose,
                                  MultiBufferSource buffers, int light, int overlay) {
        if (entity.getLevel() == null || entity.isRemoved()
                || !entity.getBlockState().getValue(SwordQiPedestalBlock.LIT)) return;
        var a = SwordQiAppearance.get();
        double time = entity.getLevel().getGameTime() + (double) partialTick;
        // Stable per-position phase; nearby copies do not pulse in lockstep.
        double phase = time + Math.floorMod(entity.getBlockPos().asLong(), a.ribbon_ticks);
        double breath = Math.sin(phase * TAU / a.breath_ticks);
        pose.pushPose();
        pose.translate(.5, 0, .5);
        pose.mulPose(Axis.YP.rotationDegrees(-entity.getBlockState().getValue(SwordQiPedestalBlock.FACING).toYRot()));
        VertexConsumer out = buffers.getBuffer(RenderType.entityTranslucent(GLOW));

        ring(pose, out, a.ring_radius, a.ring_y, a.ring_width, phase * TAU / a.ribbon_ticks,
                a.ring_samples, a.color("gold"), 175, overlay);
        ring(pose, out, a.ring_radius * .82, a.ring_y + .012, a.ring_width * .45, 0,
                a.ring_samples, a.color("jade"), 130, overlay);
        for (int stream = 0; stream < 2; stream++) {
            swirl(pose, out, a, phase, stream, true, overlay);
            swirl(pose, out, a, phase, stream, false, overlay);
        }
        pose.pushPose();
        pose.translate(0, breath * a.bob_height, 0);
        // A broad soft edge follows the complete blade outline rather than forming a light column.
        halo(pose, out, a.blade, a.halo_width, a.color("jade"), (int) (46 + breath * 7), overlay);
        halo(pose, out, a.grip, a.halo_width * .45, a.color("jade"), 32, overlay);
        sword(pose, out, a.blade, a, overlay);
        sword(pose, out, a.grip, a, overlay);
        guard(pose, out, a, overlay);
        inscription(pose, out, a, overlay);
        pose.popPose();

        // Flush the first texture's vertices before requesting a different buffer.
        VertexConsumer motes = buffers.getBuffer(RenderType.entityTranslucent(MOTE));
        for (int i = 0; i < a.motes; i++) {
            double t = (phase / a.mote_ticks + i / (double) a.motes) % 1;
            double theta = i * 2.3999632297 + t * TAU * .35;
            double radius = a.ribbon_radius * (.48 + .30 * Math.sin(i * 1.7));
            double x = Math.cos(theta) * radius, z = Math.sin(theta) * radius;
            double y = a.ribbon_bottom + t * a.ribbon_height;
            double size = a.mote_size * (.6 + .4 * Math.sin(Math.PI * t));
            int alpha = (int) (180 * Math.pow(Math.sin(Math.PI * t), .8));
            spark(pose, motes, x, y, z, size, a.color(i % 3 == 0 ? "gold" : "edge"), alpha, overlay);
        }
        pose.popPose();
    }

    private static void sword(PoseStack p, VertexConsumer out, float[][] profile, SwordQiAppearance a, int overlay) {
        for (int i = 0; i < profile.length - 1; i++) {
            var b = profile[i]; var t = profile[i + 1];
            // All four facets meet along one connected solid silhouette, including the tip.
            quad(p, out, -b[1], b[0], 0, 0, b[0], -b[2], 0, t[0], -t[2], -t[1], t[0], 0,
                    a.color("core"), 230, false, overlay);
            quad(p, out, 0, b[0], -b[2], b[1], b[0], 0, t[1], t[0], 0, 0, t[0], -t[2],
                    a.color("jade"), 215, false, overlay);
            quad(p, out, b[1], b[0], 0, 0, b[0], b[2], 0, t[0], t[2], t[1], t[0], 0,
                    a.color("edge"), 220, false, overlay);
            quad(p, out, 0, b[0], b[2], -b[1], b[0], 0, -t[1], t[0], 0, 0, t[0], t[2],
                    a.color("jade"), 215, false, overlay);
            for (int sign : new int[]{-1, 1}) {
                double w = .012;
                quad(p, out, sign * b[1] - w, b[0], -.002, sign * b[1] + w, b[0], -.002,
                        sign * t[1] + w, t[0], -.002, sign * t[1] - w, t[0], -.002,
                        a.color("edge"), 240, false, overlay);
            }
        }
    }

    private static void halo(PoseStack p, VertexConsumer out, float[][] profile, double width, int color, int alpha, int overlay) {
        for (int plane = 0; plane < 2; plane++) {
            p.pushPose();
            p.mulPose(Axis.YP.rotationDegrees(plane * 90));
            for (int i = 0; i < profile.length - 1; i++) {
                var b = profile[i]; var t = profile[i + 1];
                double lower = b[1] + width * Math.min(1, b[1] / .12);
                double upper = t[1] + width * Math.min(1, t[1] / .12);
                quad(p, out, -lower, b[0], 0, lower, b[0], 0,
                        upper, t[0], 0, -upper, t[0], 0, color, alpha, true, overlay);
            }
            p.popPose();
        }
    }

    private static void guard(PoseStack p, VertexConsumer out, SwordQiAppearance a, int overlay) {
        for (int sign : new int[]{-1, 1}) for (int i = 0; i < 24; i++) {
            double t0 = i / 24., t1 = (i + 1) / 24.;
            double x0 = sign * t0 * a.guard_width, x1 = sign * t1 * a.guard_width;
            double y0 = a.guard_y + a.guard_sweep * t0 * t0, y1 = a.guard_y + a.guard_sweep * t1 * t1;
            double w0 = a.guard_thickness * (1 - t0 * .9), w1 = a.guard_thickness * (1 - t1 * .9);
            quad(p, out, x0, y0 - w0, -.06, x0, y0 + w0, -.06,
                    x1, y1 + w1, -.035, x1, y1 - w1, -.035, a.color("gold"), 230, false, overlay);
            quad(p, out, x0, y0 - w0 * 5, 0, x0, y0 + w0 * 5, 0,
                    x1, y1 + w1 * 5, 0, x1, y1 - w1 * 5, 0, a.color("gold"), 65, true, overlay);
        }
    }

    private static void inscription(PoseStack p, VertexConsumer out, SwordQiAppearance a, int overlay) {
        // One fine central seal line and three connected rhombi on both sides of the blade.
        for (int side : new int[]{-1, 1}) {
            for (int i = 0; i < 3; i++) {
                double y = 2.05 + i * .33, z = side * .112;
                line(p, out, -.04, y, z, 0, y + .10, z, .009, a.color("gold"), 200, overlay);
                line(p, out, 0, y + .10, z, .04, y, z, .009, a.color("gold"), 200, overlay);
                line(p, out, .04, y, z, 0, y - .10, z, .009, a.color("gold"), 200, overlay);
                line(p, out, 0, y - .10, z, -.04, y, z, .009, a.color("gold"), 200, overlay);
            }
            line(p, out, 0, 1.92, side * .114, 0, 3.39, side * .114, .005, a.color("gold"), 155, overlay);
        }
    }

    private static void swirl(PoseStack p, VertexConsumer out, SwordQiAppearance a, double phase,
                              int stream, boolean halo, int overlay) {
        int samples = a.ribbon_samples;
        for (int i = 0; i < samples; i++) {
            double t0 = i / (double) samples, t1 = (i + 1) / (double) samples;
            double angle0 = phase * TAU / a.ribbon_ticks + stream * Math.PI + t0 * TAU * a.ribbon_turns;
            double angle1 = phase * TAU / a.ribbon_ticks + stream * Math.PI + t1 * TAU * a.ribbon_turns;
            double radius0 = a.ribbon_radius * Math.sin(Math.PI * (.18 + .75 * t0));
            double radius1 = a.ribbon_radius * Math.sin(Math.PI * (.18 + .75 * t1));
            double x0 = Math.cos(angle0) * radius0, z0 = Math.sin(angle0) * radius0;
            double x1 = Math.cos(angle1) * radius1, z1 = Math.sin(angle1) * radius1;
            double y0 = a.ribbon_bottom + t0 * a.ribbon_height, y1 = a.ribbon_bottom + t1 * a.ribbon_height;
            double envelope = Math.pow(Math.sin(Math.PI * (t0 + t1) * .5), .7);
            double width = a.ribbon_width * envelope * (halo ? 2.6 : 1);
            int alpha = (int) ((halo ? 32 : stream == 0 ? 165 : 135) * envelope);
            quad(p, out, x0, y0 - width, z0, x0, y0 + width, z0,
                    x1, y1 + width, z1, x1, y1 - width, z1,
                    a.color(stream == 0 ? "edge" : "gold"), alpha, true, overlay);
        }
    }

    private static void ring(PoseStack p, VertexConsumer out, double radius, double y, double width,
                             double phase, int samples, int color, int alpha, int overlay) {
        for (int i = 0; i < samples; i++) {
            double u = i * TAU / samples, v = (i + 1) * TAU / samples;
            int opacity = (int) (alpha * (.68 + .32 * Math.cos(u * 3 + phase)));
            quad(p, out, Math.cos(u) * (radius - width), y, Math.sin(u) * (radius - width),
                    Math.cos(u) * (radius + width), y, Math.sin(u) * (radius + width),
                    Math.cos(v) * (radius + width), y, Math.sin(v) * (radius + width),
                    Math.cos(v) * (radius - width), y, Math.sin(v) * (radius - width), color, opacity, true, overlay);
        }
    }

    private static void line(PoseStack p, VertexConsumer out, double x0, double y0, double z0,
                             double x1, double y1, double z1, double width, int color, int alpha, int overlay) {
        double length = Math.hypot(x1 - x0, y1 - y0);
        double dx = (y1 - y0) / length * width, dy = -(x1 - x0) / length * width;
        quad(p, out, x0 - dx, y0 - dy, z0, x0 + dx, y0 + dy, z0,
                x1 + dx, y1 + dy, z1, x1 - dx, y1 - dy, z1, color, alpha, false, overlay);
    }

    private static void spark(PoseStack p, VertexConsumer out, double x, double y, double z,
                              double size, int color, int alpha, int overlay) {
        // Three tiny crossed soft sprites retain volume from every view; no shard geometry.
        for (int plane = 0; plane < 3; plane++) {
            p.pushPose(); p.translate(x, y, z); p.mulPose(Axis.YP.rotationDegrees(plane * 60));
            vertex(p, out, -size, -size, 0, 0, 0, color, alpha, overlay);
            vertex(p, out, size, -size, 0, 1, 0, color, alpha, overlay);
            vertex(p, out, size, size, 0, 1, 1, color, alpha, overlay);
            vertex(p, out, -size, size, 0, 0, 1, color, alpha, overlay);
            p.popPose();
        }
    }

    private static void quad(PoseStack p, VertexConsumer out,
                             double x0, double y0, double z0, double x1, double y1, double z1,
                             double x2, double y2, double z2, double x3, double y3, double z3,
                             int color, int alpha, boolean feather, int overlay) {
        vertex(p, out, x0, y0, z0, feather ? 0 : .5f, .5f, color, alpha, overlay);
        vertex(p, out, x1, y1, z1, feather ? 1 : .5f, .5f, color, alpha, overlay);
        vertex(p, out, x2, y2, z2, feather ? 1 : .5f, .5f, color, alpha, overlay);
        vertex(p, out, x3, y3, z3, feather ? 0 : .5f, .5f, color, alpha, overlay);
    }
    private static void vertex(PoseStack p, VertexConsumer out, double x, double y, double z,
                                float u, float v, int color, int alpha, int overlay) {
        out.addVertex(p.last(), (float) x, (float) y, (float) z)
                .setColor((alpha << 24) | color).setUv(u, v).setOverlay(overlay).setLight(FULL_BRIGHT)
                .setNormal(p.last(), 0, 1, 0);
    }

    @Override public AABB getRenderBoundingBox(SwordQiPedestalEntity entity) {
        var a = SwordQiAppearance.get();
        double radius = Math.max(a.ribbon_radius + a.ribbon_width * 2.6, a.guard_width + a.halo_width);
        return new AABB(entity.getBlockPos()).inflate(radius, 0, radius)
                .expandTowards(0, a.blade[a.blade.length - 1][0] + a.bob_height, 0);
    }
    @Override public int getViewDistance() { return SwordQiAppearance.get().view_distance; }
}
