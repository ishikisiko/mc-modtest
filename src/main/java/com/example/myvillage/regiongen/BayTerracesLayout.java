package com.example.myvillage.regiongen;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Immutable, versioned inputs shared by structure siting and chunk realization. */
public final class BayTerracesLayout {
    public static final String RESOURCE = "/data/myvillage/regiongen/bay_terraces_v1.json";
    public record Point(int x, int z) {}
    public record Tile(int x, int z, String template, String sha256) {}

    public final int size, margin, blendWidth, baseY, height, waterY, soilDepth;
    public final int anchorLevel, maxRelief, minAboveSea, maxSurface, salt;
    public final String id, layoutHash;
    private final int[] heights, kinds, sampleOffsets;
    private final Map<Long, Tile> tiles;
    private final Map<String, String> materials;

    private static final class Holder {
        private static final BayTerracesLayout INSTANCE = load();
    }

    public static BayTerracesLayout current() { return Holder.INSTANCE; }

    private static BayTerracesLayout load() {
        try (var input = BayTerracesLayout.class.getResourceAsStream(RESOURCE)) {
            if (input == null) throw new IllegalStateException("Missing bay settlement assets");
            return new BayTerracesLayout(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read bay settlement assets", e);
        }
    }

    public BayTerracesLayout(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        if (integer(root, "schema", 1, 1) != 1) throw new IllegalArgumentException("Unsupported bay schema");
        id = root.get("id").getAsString();
        if (!id.equals("bay_terraces_v1")) throw new IllegalArgumentException("Unsupported bay layout");
        layoutHash = root.get("layout_hash").getAsString();
        if (!layoutHash.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Invalid layout hash");
        size = integer(root, "size", 16, 224);
        baseY = integer(root, "base_y", 0, 128);
        height = integer(root, "height", 1, 160);
        waterY = integer(root, "water_y", baseY, baseY + height - 1);
        soilDepth = integer(root, "soil_depth", 1, 16);
        JsonObject settings = root.getAsJsonObject("worldgen");
        integer(settings, "layout_version", 1, 1);
        margin = integer(settings, "margin", 0, 64);
        blendWidth = integer(settings, "blend_width", 1, size / 2);
        if (size % 16 != 0 || margin % 16 != 0 || size + 2 * margin > 256)
            throw new IllegalArgumentException("Bay footprint exceeds native chunk-reference bounds");
        anchorLevel = integer(settings, "anchor_level", baseY, baseY + height - 1);
        maxRelief = integer(settings, "max_relief", 1, 64);
        minAboveSea = integer(settings, "min_above_sea", 0, 32);
        maxSurface = integer(settings, "max_surface", anchorLevel, 240);
        salt = integer(settings, "salt", 0, Integer.MAX_VALUE);
        sampleOffsets = array(settings, "sample_offsets", 3, -size / 2, size / 2);
        heights = array(root, "heights", size * size, baseY + 1, baseY + height - 1);
        kinds = array(root, "kinds", size * size, 0, 2);
        Map<String, String> materialMap = new HashMap<>();
        for (String key : new String[]{"ground", "soil", "rock", "bank", "court", "road", "water"}) {
            String state = root.getAsJsonObject("materials").get(key).getAsString();
            if (!state.matches("minecraft:[a-z0-9_]+")) throw new IllegalArgumentException("Invalid bay material");
            materialMap.put(key, state);
        }
        materials = Map.copyOf(materialMap);
        Map<Long, Tile> tileMap = new HashMap<>();
        for (var item : root.getAsJsonArray("tiles")) {
            JsonObject t = item.getAsJsonObject();
            int x = integer(t, "x", 0, size - 16), z = integer(t, "z", 0, size - 16);
            String template = t.get("template").getAsString(), sha = t.get("sha256").getAsString();
            if (x % 16 != 0 || z % 16 != 0 || !sha.matches("[0-9a-f]{64}")
                    || !template.equals("myvillage:regiongen/" + id + "/tile_" + x / 16 + "_" + z / 16))
                throw new IllegalArgumentException("Invalid bay chunk tile");
            if (tileMap.put(key(x, z), new Tile(x, z, template, sha)) != null)
                throw new IllegalArgumentException("Duplicate bay chunk tile");
        }
        if (tileMap.isEmpty()) throw new IllegalArgumentException("Bay layout has no buildings");
        tiles = Map.copyOf(tileMap);
    }

    private static int integer(JsonObject object, String name, int min, int max) {
        var value = object.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
            throw new IllegalArgumentException("Missing numeric bay field " + name);
        double d = value.getAsDouble();
        if (!Double.isFinite(d) || d != Math.rint(d) || d < min || d > max)
            throw new IllegalArgumentException("Out-of-range bay field " + name);
        return (int) d;
    }

    private static int[] array(JsonObject root, String name, int length, int min, int max) {
        var source = root.getAsJsonArray(name);
        if (source == null || source.size() != length) throw new IllegalArgumentException("Invalid bay array " + name);
        int[] result = new int[length];
        for (int i = 0; i < length; i++) {
            double value = source.get(i).getAsDouble();
            if (value != Math.rint(value) || value < min || value > max)
                throw new IllegalArgumentException("Invalid bay array value " + name);
            result[i] = (int) value;
        }
        return result;
    }

    private static long key(int x, int z) { return ((long) x << 32) ^ (z & 0xffffffffL); }
    private int index(int x, int z) {
        return Math.clamp(z, 0, size - 1) * size + Math.clamp(x, 0, size - 1);
    }
    public int[] sampleOffsets() { return sampleOffsets.clone(); }
    public int sourceHeight(int x, int z) { return heights[index(x, z)]; }
    public String material(String role) { return materials.get(role); }
    public Tile tile(int x, int z) { return tiles.get(key(x, z)); }
    public int tileCount() { return tiles.size(); }

    public double weight(int x, int z) {
        int distance = Math.min(Math.min(x + margin, z + margin),
                Math.min(size + margin - 1 - x, size + margin - 1 - z));
        double t = Math.clamp(distance / (double) blendWidth, 0, 1);
        return t * t * (3 - 2 * t);
    }

    public int surface(int x, int z, int natural, int offsetY) {
        double weight = weight(x, z);
        return (int) Math.round(natural * (1 - weight) + (sourceHeight(x, z) + offsetY) * weight);
    }

    public String surfaceMaterial(int x, int z, int target, int offsetY) {
        if (target <= waterY + offsetY + 1) return material("bank");
        return material(switch (kinds[index(x, z)]) { case 1 -> "court"; case 2 -> "road"; default -> "ground"; });
    }

    public Point rotate(int x, int z, int quarterTurns) {
        if (quarterTurns < 0 || quarterTurns > 3) throw new IllegalArgumentException("Invalid bay rotation");
        for (int i = 0; i < quarterTurns; i++) { int nx = size - 1 - z; z = x; x = nx; }
        return new Point(x, z);
    }
    public Point inverse(int x, int z, int quarterTurns) { return rotate(x, z, (4 - quarterTurns) & 3); }

    public long siteSeed(long worldSeed, int chunkX, int chunkZ) {
        long value = worldSeed ^ key(chunkX, chunkZ) ^ salt;
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }
}
