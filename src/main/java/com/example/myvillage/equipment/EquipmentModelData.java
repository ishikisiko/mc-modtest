package com.example.myvillage.equipment;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/** Bounded, client-type-free description of rigid cuboids attached to humanoid bones. */
public record EquipmentModelData(ResourceLocation id, ResourceLocation texture, int textureSize, int density,
                                 Map<String, Map<ResourceLocation, Piece>> profiles) {
    private static final Map<String, Set<String>> BONES = Map.of(
            "head", Set.of("head"), "chest", Set.of("body", "right_arm", "left_arm"),
            "legs", Set.of("body", "right_leg", "left_leg"), "feet", Set.of("right_leg", "left_leg"));

    public record Vec(float x, float y, float z) {
        static final Vec ZERO = new Vec(0, 0, 0);
    }
    public record Box(String name, String bone, Vec from, Vec size, int u, int v, Vec pivot, Vec rotation) { }
    public record Piece(ResourceLocation id, String slot, List<Box> boxes) { }

    public static EquipmentModelData parse(Reader reader) {
        var root = JsonParser.parseReader(reader).getAsJsonObject();
        if (integer(root, "version") != 1) throw new IllegalArgumentException("Equipment model version");
        int size = integer(root, "texture_size"), density = integer(root, "texel_density");
        if (!Set.of(64, 128, 256, 512).contains(size) || !Set.of(1, 2, 4).contains(density))
            throw new IllegalArgumentException("Equipment texture dimensions");
        var profiles = new LinkedHashMap<String, Map<ResourceLocation, Piece>>();
        var sourceProfiles = root.getAsJsonObject("profiles");
        if (!sourceProfiles.keySet().equals(Set.of("standard", "slim")))
            throw new IllegalArgumentException("Equipment needs exactly standard and slim profiles");
        for (String profile : List.of("standard", "slim")) {
            var pieces = new LinkedHashMap<ResourceLocation, Piece>();
            var source = sourceProfiles.getAsJsonArray(profile);
            if (source.isEmpty() || source.size() > 4) throw new IllegalArgumentException("Armor piece count");
            for (var element : source) {
                var piece = element.getAsJsonObject();
                var id = ResourceLocation.parse(piece.get("id").getAsString());
                String slot = piece.get("slot").getAsString();
                if (!BONES.containsKey(slot) || pieces.containsKey(id)) throw new IllegalArgumentException("Armor piece identity");
                var sourceBoxes = piece.getAsJsonArray("boxes");
                if (sourceBoxes.isEmpty() || sourceBoxes.size() > 256) throw new IllegalArgumentException("Armor box count");
                var boxes = new ArrayList<Box>();
                var names = new HashSet<String>();
                for (var boxElement : sourceBoxes) {
                    var box = boxElement.getAsJsonObject();
                    String name = box.get("name").getAsString(), bone = box.get("bone").getAsString();
                    if (name.isBlank() || !names.add(name) || !BONES.get(slot).contains(bone))
                        throw new IllegalArgumentException("Invalid armor bone/name " + name);
                    var from = vector(box.getAsJsonArray("from"), false);
                    var extent = vector(box.getAsJsonArray("size"), true);
                    var uv = box.getAsJsonArray("uv");
                    if (uv.size() != 2) throw new IllegalArgumentException("Armor UV pair");
                    int u = uv.get(0).getAsBigDecimal().intValueExact(), v = uv.get(1).getAsBigDecimal().intValueExact();
                    if (u < 0 || v < 0 || u + 2 * (extent.x + extent.z) * density > size
                            || v + (extent.y + extent.z) * density > size)
                        throw new IllegalArgumentException("Armor UV exceeds texture");
                    var pivot = box.has("pivot") ? vector(box.getAsJsonArray("pivot"), false) : Vec.ZERO;
                    var rotation = box.has("rotation") ? vector(box.getAsJsonArray("rotation"), false) : Vec.ZERO;
                    boxes.add(new Box(name, bone, from, extent, u, v, pivot, rotation));
                }
                pieces.put(id, new Piece(id, slot, List.copyOf(boxes)));
            }
            profiles.put(profile, Map.copyOf(pieces));
        }
        if (!profiles.get("standard").keySet().equals(profiles.get("slim").keySet()))
            throw new IllegalArgumentException("Armor profile identities differ");
        for (var entry : profiles.get("standard").entrySet()) {
            if (!entry.getValue().slot.equals(profiles.get("slim").get(entry.getKey()).slot))
                throw new IllegalArgumentException("Armor profile slots differ");
        }
        return new EquipmentModelData(ResourceLocation.parse(root.get("id").getAsString()),
                ResourceLocation.parse(root.get("texture").getAsString()), size, density, Map.copyOf(profiles));
    }

    private static int integer(JsonObject object, String key) { return object.get(key).getAsBigDecimal().intValueExact(); }
    private static Vec vector(JsonArray array, boolean positive) {
        if (array.size() != 3) throw new IllegalArgumentException("Armor vector length");
        float[] result = new float[3];
        for (int i = 0; i < 3; i++) {
            result[i] = array.get(i).getAsFloat();
            if (!Float.isFinite(result[i]) || Math.abs(result[i]) > 64 || (positive && result[i] <= 0))
                throw new IllegalArgumentException("Armor vector bounds");
        }
        return new Vec(result[0], result[1], result[2]);
    }
}
