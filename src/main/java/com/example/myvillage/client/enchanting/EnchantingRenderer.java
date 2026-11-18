package com.example.myvillage.client.enchanting;

import com.example.myvillage.enchanting.EnchantingDevice;
import com.example.myvillage.enchanting.EnchantingRules;
import com.example.myvillage.enchanting.EnchantingStructure;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;

public final class EnchantingRenderer implements BlockEntityRenderer<EnchantingDevice> {
    private static final int FULL_LIGHT = 0xF000F0;
    private static final int[][] FACES = {{0, 4, 6, 2}, {1, 3, 7, 5}, {0, 1, 5, 4},
            {2, 6, 7, 3}, {0, 2, 3, 1}, {4, 5, 7, 6}};
    private static final Map<String, RenderType> MATERIALS = new HashMap<>();
    private static final RingLayout LIBRARY = EnchantingRules.read("rings", RingLayout.class);
    private static final float[] PERIODS = {1800, 1440, 1200};
    private final Map<EnchantingDevice, Motion> motions = new WeakHashMap<>();

    private record RingLayout(float[] center, Ring[] rings) {}
    private record Ring(String id, float r, float w, float d, float[] u, float[] v, float[] n, String main) {}

    private static final class Motion {
        float from;
        int target;
        float started;
        long spark = -1;
        float at(float time, int desired) {
            float t = (time < started ? 1 : Mth.clamp((time - started) / 21F, 0, 1));
            float value = Mth.lerp(t * t * (3 - 2 * t), from, target);
            if (target != desired) {
                from = value;
                target = desired;
                started = time;
            }
            return value;
        }
    }

    public EnchantingRenderer(BlockEntityRendererProvider.Context context) {}

    @Override public void render(EnchantingDevice device, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        var level = device.getLevel();
        if (level == null || !device.formed()) return;
        var motion = motions.computeIfAbsent(device, key -> new Motion());
        float time = (level.getGameTime() % 72000) + partial;
        Direction front = device.front();
        pose.pushPose();
        pose.translate(.5, 0, .5);
        pose.mulPose(Axis.YP.rotationDegrees(-front.toYRot()));
        if (device.library()) {
            pose.translate(0, -1, -5);
            library(pose, buffers, light, time);
            long tick = level.getGameTime();
            if (tick % 5 == 0 && motion.spark != tick) {
                motion.spark = tick;
                var center = EnchantingStructure.libraryCenter(device.getBlockPos(), front);
                float a = time * .033f;
                level.addParticle(ParticleTypes.END_ROD, center.getX() + .5 + 3.4 * Mth.cos(a),
                        center.getY() + 7.2, center.getZ() + .5 + 3.4 * Mth.sin(a), 0, .017, 0);
            }
        } else {
            seal(pose, buffers, light, motion.at(time, device.pose()), time);
        }
        pose.popPose();
    }

    @Override public AABB getRenderBoundingBox(EnchantingDevice device) {
        var pos = device.library() ? EnchantingStructure.libraryCenter(device.getBlockPos(), device.front()) : device.getBlockPos();
        return new AABB(pos.getX() - 6, pos.getY() - 1, pos.getZ() - 6, pos.getX() + 7, pos.getY() + 14, pos.getZ() + 7);
    }

    @Override public int getViewDistance() { return 80; }

    static void library(PoseStack pose, MultiBufferSource buffers, int light, float time) {
        pose.pushPose();
        pose.translate(0, LIBRARY.center()[1], 0);
        var rings = LIBRARY.rings();
        for (int index = 0; index < rings.length; index++) {
            var ring = rings[index];
            float phase = (float) (time * Math.PI * 2 / PERIODS[index]) * (index == 1 ? -1 : 1);
            String tone = index == 1 ? "jade" : index == 2 ? "rose" : ring.main();
            for (int i = 0; i < 48; i++) {
                float angle = phase + (float) (i * Math.PI / 24);
                segment(pose, buffers, ring, angle, .126f, ring.r() - ring.w() / 2,
                        ring.r() + ring.w() / 2, -ring.d() / 2, ring.d() / 2, i % 12 == 0 ? "brass" : tone, light);
            }
            for (int i = 0; i < 12; i++) {
                float moving = phase + (float) (i * Math.PI / 6);
                segment(pose, buffers, ring, moving, .11f, ring.r() - ring.w() * .42f,
                        ring.r() + ring.w() * .42f, -ring.d() * .52f, ring.d() * .52f,
                        i % 3 == 0 ? "glow" : "brass", i % 3 == 0 ? FULL_LIGHT : light);
            }
        }
        pose.mulPose(Axis.YP.rotationDegrees(time / 8));
        float pulse = 1 + .07f * Mth.sin(time / 14);
        pose.scale(pulse, pulse, pulse);
        box(pose, buffers, -.46f, -.65f, -.46f, .92f, 1.3f, .92f, "jade", light);
        box(pose, buffers, -.25f, -.49f, -.48f, .5f, .98f, .96f, "glow", FULL_LIGHT);
        pose.popPose();
    }

    private static void segment(PoseStack pose, MultiBufferSource buffers, Ring ring, float angle, float arc,
            float inner, float outer, float back, float front, String material, int light) {
        var vertices = new Vector3f[8];
        for (int i = 0; i < 8; i++) {
            float q = angle + ((i & 1) == 0 ? 0 : arc);
            float radius = (i & 2) == 0 ? inner : outer;
            float u = Mth.cos(q) * radius;
            float v = Mth.sin(q) * radius;
            float n = (i & 4) == 0 ? back : front;
            vertices[i] = new Vector3f(ring.u()[0] * u + ring.v()[0] * v + ring.n()[0] * n,
                    ring.u()[1] * u + ring.v()[1] * v + ring.n()[1] * n,
                    ring.u()[2] * u + ring.v()[2] * v + ring.n()[2] * n);
        }
        draw(pose, buffers, vertices, material, light);
    }

    static void seal(PoseStack pose, MultiBufferSource buffers, int light, float phase, float time) {
        for (int i = 0; i < 4; i++) {
            float angle = 45 + 90 * i;
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(-angle));
            pose.translate(5.25f, 3.60f, 0);
            pose.mulPose(Axis.YP.rotationDegrees(blend(19, -18, 0, phase)));
            pose.mulPose(Axis.ZP.rotationDegrees(blend(-41, -13, 13, phase)));
            float slide = blend(0, .3f, 1.18f, phase);
            box(pose, buffers, -.52f, -.19f, -.42f, .68f, .37f, .84f, "bronze", light);
            box(pose, buffers, -1.62f, -.21f, -.52f, 1.25f, .42f, 1.04f, "slate", light);
            box(pose, buffers, -1.39f, .204f, -.31f, .72f, .11f, .62f, "jade", light);
            box(pose, buffers, -2.60f - slide, -.13f, -.27f, 1.32f + slide, .26f, .54f, "bronze", light);
            box(pose, buffers, -2.34f - slide, -.21f, -.54f, .86f, .43f, 1.08f, "jade", light);
            box(pose, buffers, -3.17f - slide, -.17f, -.43f, .90f, .35f, .86f, "slate", light);
            for (float side : new float[]{-.47f, .38f})
                box(pose, buffers, -2.7f - slide, .209f, side, 1.11f, .07f, .09f, "brass", light);
            box(pose, buffers, -2.32f - slide, .225f, -.25f, .48f, .065f, .5f, "violet", phase > .8f ? FULL_LIGHT : light);
            pose.popPose();
        }
        pose.pushPose();
        pose.translate(0, blend(4.63f, 3.93f, 2.67f, phase) + .055f * Mth.sin(time / 8), 0);
        pose.mulPose(Axis.YP.rotationDegrees(blend(-24, -10, 0, phase)));
        pose.mulPose(Axis.XP.rotationDegrees(blend(26, 13, 0, phase)));
        box(pose, buffers, -.29f, .42f, -.29f, .58f, .28f, .58f, "brass", light);
        box(pose, buffers, -.58f, .12f, -.58f, 1.16f, .32f, 1.16f, "slate", light);
        box(pose, buffers, -.47f, -.16f, -.47f, .94f, .30f, .94f, "violet", phase > .7f ? FULL_LIGHT : light);
        box(pose, buffers, -.33f, -.27f, -.33f, .66f, .13f, .66f, "glow", FULL_LIGHT);
        pose.popPose();
        if (phase > 1.7f) for (int i = 0; i < 4; i++) {
            float h = 1.08f + i * .33f + .10f * Mth.sin(time * .7f);
            box(pose, buffers, -.045f, h, -.045f, .09f, .15f, .09f, "glow", FULL_LIGHT);
        }
    }

    private static float blend(float a, float b, float c, float t) { return t <= 1 ? Mth.lerp(t, a, b) : Mth.lerp(t - 1, b, c); }

    static void box(PoseStack pose, MultiBufferSource buffers, float x, float y, float z,
            float w, float h, float d, String material, int light) {
        var vertices = new Vector3f[8];
        for (int i = 0; i < 8; i++)
            vertices[i] = new Vector3f(x + ((i & 1) == 0 ? 0 : w), y + ((i & 2) == 0 ? 0 : h), z + ((i & 4) == 0 ? 0 : d));
        draw(pose, buffers, vertices, material, light);
    }

    private static void draw(PoseStack pose, MultiBufferSource buffers, Vector3f[] points, String material, int light) {
        VertexConsumer out = buffers.getBuffer(MATERIALS.computeIfAbsent(material, key -> RenderType.entityCutoutNoCull(
                EnchantingRules.id("textures/block/enchanting/" + key + ".png"))));
        for (int[] quad : FACES) {
            var a = points[quad[0]];
            var n = points[quad[1]].sub(a, new Vector3f()).cross(points[quad[2]].sub(a, new Vector3f())).normalize();
            for (int i = 0; i < 4; i++) {
                var v = points[quad[i]];
                out.addVertex(pose.last(), v.x, v.y, v.z).setColor(-1).setUv(i == 1 || i == 2 ? 1 : 0, i >= 2 ? 1 : 0)
                        .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose.last(), n.x, n.y, n.z);
            }
        }
    }
}
