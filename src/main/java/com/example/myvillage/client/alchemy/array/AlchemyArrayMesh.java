package com.example.myvillage.client.alchemy.array;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

/** The mesh is generated from tools/alchemy_array/formed_model.py. No independent
 * geometry copy in Java: the same JSON is also used for offline model review. */
final class AlchemyArrayMesh implements ResourceManagerReloadListener {
    static final AlchemyArrayMesh INSTANCE = new AlchemyArrayMesh();
    static final ResourceLocation LOCATION = ResourceLocation.fromNamespaceAndPath("myvillage", "alchemy_array/formed_mesh.json");
    record Face(float[][] points, float[] normal, int material, boolean lid, boolean glow) { }
    private volatile List<Face> faces = List.of();
    List<Face> faces() { return faces; }

    @Override public void onResourceManagerReload(ResourceManager manager) {
        var parsed = new ArrayList<Face>();
        try (var stream = manager.getResourceOrThrow(LOCATION).open();
             var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            var root = JsonParser.parseReader(reader).getAsJsonObject();
            if (root.get("version").getAsInt() != 1) throw new IllegalArgumentException("Unsupported cauldron mesh version");
            for (var item : root.getAsJsonArray("faces")) {
                var face = item.getAsJsonObject();
                var points = face.getAsJsonArray("p");
                if (points.size() != 4) throw new IllegalArgumentException("Cauldron mesh requires quads");
                float[][] vertices = new float[4][3];
                for (int i=0; i<4; i++) for (int axis=0; axis<3; axis++) {
                    float value=points.get(i).getAsJsonArray().get(axis).getAsFloat();
                    if (!Float.isFinite(value) || Math.abs(value)>8f) throw new IllegalArgumentException("Invalid cauldron coordinate");
                    vertices[i][axis]=value;
                }
                float[] normal=new float[3];
                for(int axis=0;axis<3;axis++) normal[axis]=face.getAsJsonArray("n").get(axis).getAsFloat();
                int material=face.get("m").getAsInt();
                if(material<0 || material>15)throw new IllegalArgumentException("Invalid cauldron material");
                parsed.add(new Face(vertices,normal,material,face.get("g").getAsString().equals("lid"),face.get("glow").getAsBoolean()));
            }
            if(parsed.isEmpty() || parsed.size()>10000) throw new IllegalArgumentException("Invalid cauldron face count");
        } catch (Exception error) { throw new IllegalStateException("Cannot load sculpted alchemy array mesh", error); }
        faces=List.copyOf(parsed);
    }
}
