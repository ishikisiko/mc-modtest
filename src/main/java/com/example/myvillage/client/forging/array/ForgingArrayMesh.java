package com.example.myvillage.client.forging.array;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

/** 与离线预览共用生成的网格，不在 Java 复制鼎体几何。 */
final class ForgingArrayMesh implements ResourceManagerReloadListener {
    static final ForgingArrayMesh INSTANCE = new ForgingArrayMesh();
    static final ResourceLocation LOCATION =
            ResourceLocation.fromNamespaceAndPath("myvillage", "forging_array/formed_mesh.json");

    enum Group { BODY, RING, FIRE }
    record Face(float[][] points, float[] normal, int material, Group group, boolean glow) { }

    private volatile List<Face> faces = List.of();
    List<Face> faces() { return faces; }

    @Override public void onResourceManagerReload(ResourceManager manager) {
        var parsed = new ArrayList<Face>();
        try (var stream = manager.getResourceOrThrow(LOCATION).open();
             var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            var root = JsonParser.parseReader(reader).getAsJsonObject();
            if (root.get("version").getAsInt() != 1)
                throw new IllegalArgumentException("Unsupported forging array mesh version");
            var source = root.getAsJsonArray("faces");
            if (source.isEmpty() || source.size() > 10000)
                throw new IllegalArgumentException("Invalid forging array face count");
            for (var item : source) {
                var face = item.getAsJsonObject();
                var points = face.getAsJsonArray("p");
                if (points.size() != 4) throw new IllegalArgumentException("Forging array mesh requires quads");
                float[][] vertices = new float[4][];
                for (int i = 0; i < 4; i++) vertices[i] = vector(points.get(i).getAsJsonArray(), 8f);
                float[] normal = vector(face.getAsJsonArray("n"), 1.001f);
                float lengthSquared = normal[0] * normal[0] + normal[1] * normal[1] + normal[2] * normal[2];
                if (Math.abs(lengthSquared - 1f) > .01f)
                    throw new IllegalArgumentException("Invalid forging array normal");
                int material = face.get("m").getAsBigDecimal().intValueExact();
                if (material < 0 || material > 15)
                    throw new IllegalArgumentException("Invalid forging array material");
                Group group = switch (face.get("g").getAsString()) {
                    case "body" -> Group.BODY;
                    case "ring" -> Group.RING;
                    case "fire" -> Group.FIRE;
                    default -> throw new IllegalArgumentException("Unknown forging array mesh group");
                };
                parsed.add(new Face(vertices, normal, material, group, face.get("glow").getAsBoolean()));
            }
        } catch (Exception error) {
            throw new IllegalStateException("Cannot load forging array mesh", error);
        }
        faces = List.copyOf(parsed);
    }

    private static float[] vector(JsonArray array, float limit) {
        if (array.size() != 3) throw new IllegalArgumentException("Expected forging array vector");
        float[] result = new float[3];
        for (int axis = 0; axis < 3; axis++) {
            float value = array.get(axis).getAsFloat();
            if (!Float.isFinite(value) || Math.abs(value) > limit)
                throw new IllegalArgumentException("Invalid forging array coordinate");
            result[axis] = value;
        }
        return result;
    }
}
