package com.example.myvillage.client.enchanting;

import com.example.myvillage.enchanting.EnchantingRules;
import com.example.myvillage.enchanting.SealDisc;
import com.example.myvillage.enchanting.SealProjection;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import org.joml.Vector3f;

final class CasketRenderer {
    private static final int FULL = 0xf000f0;
    private static final JsonObject MESH = EnchantingRules.read("casket", JsonObject.class);
    private static final Map<RenderType, List<Face>> HULL = mesh("hull"), LEFT = mesh("left"),
            RIGHT = mesh("right"), STAMP = mesh("stamp"), PLINTHS = mesh("plinths");
    private static final Map<String, RenderType> GLYPHS = new LinkedHashMap<>();
    private static final RenderType INSERT = RenderType.entityCutoutNoCull(EnchantingRules.id(
            "textures/block/enchanting/casket/paper.png"));

    private record Face(float[] vertices, Vector3f normal, Vector3f u, Vector3f v) {}

    private CasketRenderer() {}

    private static Map<RenderType, List<Face>> mesh(String piece) {
        var groups = new LinkedHashMap<RenderType, List<Face>>();
        MESH.getAsJsonArray(piece).forEach(element -> {
            var row = element.getAsJsonArray();
            var material = RenderType.entityCutoutNoCull(EnchantingRules.id(
                    "textures/block/enchanting/casket/" + row.get(0).getAsString() + ".png"));
            float[] vertices = new float[row.size() - 1];
            for (int i = 0; i < vertices.length; i++) vertices[i] = row.get(i + 1).getAsFloat();
            var a = new Vector3f(vertices[0], vertices[1], vertices[2]);
            var u = new Vector3f(vertices[3], vertices[4], vertices[5]).sub(a).normalize();
            var normal = u.cross(new Vector3f(vertices[6], vertices[7], vertices[8]).sub(a), new Vector3f()).normalize();
            var v = normal.cross(u, new Vector3f());
            groups.computeIfAbsent(material, type -> new ArrayList<>()).add(new Face(vertices, normal, u, v));
        });
        return groups;
    }

    private static void draw(PoseStack pose, MultiBufferSource buffers, Map<RenderType, List<Face>> mesh, int light) {
        for (var batch : mesh.entrySet()) {
            var out = buffers.getBuffer(batch.getKey());
            for (var face : batch.getValue()) {
                int count = face.vertices().length / 3;
                if (count == 4) quad(pose, out, face, light, 0, 1, 2, 3);
                else for (int i = 1; i + 1 < count; i++) quad(pose, out, face, light, 0, i, i + 1, i + 1);
            }
        }
    }

    private static void quad(PoseStack pose, VertexConsumer out, Face face, int light, int a, int b, int c, int d) {
        emit(pose, out, face, light, a);
        emit(pose, out, face, light, b);
        emit(pose, out, face, light, c);
        emit(pose, out, face, light, d);
    }

    private static void emit(PoseStack pose, VertexConsumer out, Face face, int light, int point) {
        float[] vertices = face.vertices();
        int i = point * 3;
        float x = vertices[i], y = vertices[i + 1], z = vertices[i + 2];
        var n = face.normal();
        vertex(pose, out, x, y, z, .6f * face.u().dot(x, y, z), .6f * face.v().dot(x, y, z),
                -1, light, n.x, n.y, n.z);
    }

    private static void vertex(PoseStack pose, VertexConsumer out, float x, float y, float z,
            float u, float v, int color, int light, float nx, float ny, float nz) {
        out.addVertex(pose.last(), x, y, z).setColor(color).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose.last(), nx, ny, nz);
    }

    static void render(PoseStack pose, MultiBufferSource buffers, int light, float motion, float formation,
            List<String> discs, int target) {
        float rise = smooth(Mth.clamp(formation * 1.8f, 0, 1));
        float open = smooth(Mth.clamp((formation - .30f) / .70f, 0, 1));
        pose.pushPose();
        pose.translate(0, SealProjection.MODEL_Y, 0);
        pose.translate(0, -.14, 0);
        pose.scale(1, Mth.lerp(rise, .57f, 1), 1);
        pose.translate(0, .14, 0);
        draw(pose, buffers, HULL, light);
        draw(pose, buffers, PLINTHS, light);

        float writing = Mth.clamp(motion - 1, 0, 1);
        float angle = open * Mth.lerp(writing, .72f, .27f);
        lid(pose, buffers, light, -1, angle, LEFT);
        lid(pose, buffers, light, 1, angle, RIGHT);
        pose.pushPose();
        pose.translate(0, Mth.lerp(open, 2.05f, Mth.lerp(writing, 3.68f, 2.32f)), 0);
        draw(pose, buffers, STAMP, light);
        pose.popPose();

        for (int i = 0; i < SealProjection.SLOTS.size(); i++) {
            var slot = SealProjection.SLOTS.get(i);
            String key = discs.isEmpty() ? "empty" : discs.get(i);
            var disc = SealDisc.TYPES.getOrDefault(key, SealDisc.TYPES.get("empty"));
            int tint = disc.rgb();
            socket(pose, buffers, light, slot, key.equals("empty") ? 0x3f5552 : tint, i == target);
            if (formation > .9f) glyph(pose, buffers, slot, key, i == target);
        }
        pose.popPose();
    }

    private static float smooth(float value) { return value * value * (3 - 2 * value); }

    private static void lid(PoseStack pose, MultiBufferSource buffers, int light, int side, float angle,
            Map<RenderType, List<Face>> mesh) {
        pose.pushPose();
        pose.translate(side * 3.13, 2.35, 0);
        pose.mulPose(Axis.ZP.rotation(-side * angle));
        pose.translate(-side * 3.13, -2.35, 0);
        draw(pose, buffers, mesh, light);
        pose.popPose();
    }

    private static void glyph(PoseStack pose, MultiBufferSource buffers, SealProjection.Slot slot,
            String key, boolean selected) {
        pose.pushPose();
        pose.translate(slot.x(), slot.y() + (selected ? .09f : 0), slot.z());
        pose.mulPose(Axis.YP.rotationDegrees(slot.turn()));
        float halfWidth = selected ? .69f : .66f;
        float halfHeight = 1.04f;
        var type = GLYPHS.computeIfAbsent(key, id -> RenderType.entityTranslucent(EnchantingRules.id(
                "textures/block/enchanting/projection/" + id + ".png")));
        var out = buffers.getBuffer(type);
        int color = selected ? 0xffffffff : 0xdfffffff;
        vertex(pose, out, -halfWidth, halfHeight, 0, 0, 0, color, FULL, 0, 0, 1);
        vertex(pose, out, -halfWidth, -halfHeight, 0, 0, 1, color, FULL, 0, 0, 1);
        vertex(pose, out, halfWidth, -halfHeight, 0, 1, 1, color, FULL, 0, 0, 1);
        vertex(pose, out, halfWidth, halfHeight, 0, 1, 0, color, FULL, 0, 0, 1);
        pose.popPose();
    }

    private static void socket(PoseStack pose, MultiBufferSource buffers, int light, SealProjection.Slot slot,
            int tint, boolean selected) {
        boolean front = slot.role().equals("front");
        pose.pushPose();
        pose.translate(slot.socket_x(), front ? 1.09f : 1.075f, front ? 4.1025f : -3.994f);
        if (!front) pose.mulPose(Axis.YP.rotationDegrees(180));
        var out = buffers.getBuffer(INSERT);
        if (selected) inlay(pose, out, .120f, .193f, 0, 0xfff2cc89, FULL);
        inlay(pose, out, front ? .074f : .096f, .154f, .004f, 0xff000000 | tint, light);
        pose.popPose();
    }

    private static void inlay(PoseStack pose, VertexConsumer out, float x, float y, float z, int color, int light) {
        vertex(pose, out, -x, y, z, 0, 0, color, light, 0, 0, 1);
        vertex(pose, out, -x, -y, z, 0, 1, color, light, 0, 0, 1);
        vertex(pose, out, x, -y, z, 1, 1, color, light, 0, 0, 1);
        vertex(pose, out, x, y, z, 1, 0, color, light, 0, 0, 1);
    }
}
