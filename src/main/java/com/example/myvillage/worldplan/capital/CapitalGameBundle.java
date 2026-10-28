package com.example.myvillage.worldplan.capital;

import com.example.myvillage.worldplan.WorldPlanRules;
import com.google.gson.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Production schema, independent of the preview exchange and the legacy single-city catalogue. */
public final class CapitalGameBundle {
    private static final List<String> IDENTITY_KEYS = List.of("schema", "format", "city_id", "region_id",
            "display_name", "layout_family_id", "size", "source_offset", "placement", "terrain", "tiles", "entries", "life");
    public record Entry(String id, int x, int y, int z, String facing) { }
    public final String cityId, regionId, displayName, layoutFamily, resource, manifestHash, bundleHash;
    public final String verticalMode, terrainResource, terrainHash, topBlock, fillBlock, subsurfaceBlock, lifeLayout;
    public final int width, height, depth, anchorY, blendMargin, subsurfaceDepth;
    public final List<WorldPlanRules.Tile> tiles;
    public final List<Entry> entries;
    private final Map<Long, WorldPlanRules.Tile> tileIndex;
    private final short[] topSolid;
    private final JsonObject life;

    public CapitalGameBundle(JsonObject frozen) {
        cityId = text(frozen, "city_id"); regionId = text(frozen, "region_id");
        CapitalRegion.fromId(regionId);
        if (!cityId.equals("capital_" + regionId)) throw new IllegalArgumentException("Capital city identity");
        resource = text(frozen, "resource");
        if (!resource.equals("myvillage:capitals/v1/" + cityId + "/city.json")) throw new IllegalArgumentException("Capital manifest path");
        manifestHash = hashText(frozen, "manifest_sha256"); bundleHash = hashText(frozen, "bundle_sha256");
        JsonObject root = JsonParser.parseString(new String(readResource(resource, 8 * 1024 * 1024), StandardCharsets.UTF_8)).getAsJsonObject();
        requireManifest(root);
        displayName = text(root, "display_name"); layoutFamily = text(root, "layout_family_id");
        int[] size = point(root.getAsJsonArray("size"));
        width = size[0]; height = size[1]; depth = size[2];
        if (width < 16 || width > 2048 || depth < 16 || depth > 2048 || width % 16 != 0 || depth % 16 != 0 || height < 8 || height > 320)
            throw new IllegalArgumentException("V8 capital dimensions");
        point(root.getAsJsonArray("source_offset")); // provenance only, never applied twice
        JsonObject placement = root.getAsJsonObject("placement");
        verticalMode = text(placement, "vertical_mode");
        if (!Set.of("absolute", "sea_relative", "terrain_relative").contains(verticalMode)
                || integer(placement, "rotation_quarters", 0, 0) != 0) throw new IllegalArgumentException("Capital placement semantics");
        anchorY = integer(placement, "anchor_y", -64, 319); blendMargin = integer(placement, "blend_margin", 16, 256);
        if (regionId.equals("zhongzhou") && (!verticalMode.equals("absolute") || anchorY != 0))
            throw new IllegalArgumentException("Zhongzhou requires absolute originY=0");
        JsonObject terrain = root.getAsJsonObject("terrain");
        terrainResource = text(terrain, "resource"); terrainHash = hashText(terrain, "sha256");
        if (!terrainResource.startsWith("myvillage:capitals/v1/" + cityId + "/")
                || !text(terrain, "encoding").equals("i16le_z_major_top_solid")
                || integer(terrain, "width", width, width) != width || integer(terrain, "depth", depth, depth) != depth)
            throw new IllegalArgumentException("Capital heightfield contract");
        topBlock = blockId(terrain, "top_block"); fillBlock = blockId(terrain, "fill_block");
        subsurfaceBlock = blockId(terrain, "subsurface_block"); subsurfaceDepth = integer(terrain, "subsurface_depth", 0, 16);
        byte[] packed = readResource(terrainResource, 16 * 1024 * 1024);
        requireHash(packed, terrainHash, terrainResource);
        int length = Math.multiplyExact(width, depth) * 2;
        try (var gzip = new GZIPInputStream(new ByteArrayInputStream(packed))) {
            byte[] data = gzip.readNBytes(length + 1);
            if (data.length != length) throw new IllegalArgumentException("Capital heightfield length");
            var shorts = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
            topSolid = new short[width * depth]; shorts.get(topSolid);
            for (short top : topSolid) if (top < 0 || top >= height) throw new IllegalArgumentException("Capital top-solid height outside local volume");
        } catch (IOException e) { throw new IllegalArgumentException("Cannot decode capital heightfield", e); }
        Map<Long, WorldPlanRules.Tile> index = new HashMap<>(); List<WorldPlanRules.Tile> records = new ArrayList<>();
        JsonObject files = new JsonObject(); files.addProperty(terrainResource, terrainHash);
        JsonArray tileData = root.getAsJsonArray("tiles");
        if (tileData.size() > width * depth / 256) throw new IllegalArgumentException("Too many capital tiles");
        for (JsonElement item : tileData) {
            JsonObject tile = item.getAsJsonObject();
            int x = integer(tile, "x", 0, width - 16), z = integer(tile, "z", 0, depth - 16);
            String template = text(tile, "template"), hash = hashText(tile, "sha256");
            if (x % 16 != 0 || z % 16 != 0 || !template.matches("myvillage:province_capitals/v1/" + cityId + "/[a-z0-9_]+"))
                throw new IllegalArgumentException("Capital tile coordinates/path");
            var record = new WorldPlanRules.Tile(template, x, z, hash);
            if (index.putIfAbsent(key(x, z), record) != null) throw new IllegalArgumentException("Overlapping capital tiles");
            records.add(record); files.addProperty(templateFile(template), hash);
        }
        tileIndex = Map.copyOf(index); tiles = List.copyOf(records);
        JsonObject identity = new JsonObject(); identity.addProperty("manifest_sha256", manifestHash); identity.add("files", files);
        if (!WorldPlanRules.sha256(WorldPlanRules.canonical(identity)).equals(bundleHash)) throw new IllegalArgumentException("Capital bundle identity mismatch");
        List<Entry> points = new ArrayList<>(); Set<String> entryIds = new HashSet<>();
        JsonArray entryData = root.getAsJsonArray("entries");
        if (entryData.isEmpty() || entryData.size() > 32) throw new IllegalArgumentException("Capital needs bounded entries");
        for (JsonElement item : entryData) {
            JsonObject entry = item.getAsJsonObject(); String id = text(entry, "id");
            int[] pos = localPoint(entry.getAsJsonArray("position"));
            String facing = text(entry, "facing");
            if (!entryIds.add(id) || !Set.of("north", "south", "east", "west").contains(facing)) throw new IllegalArgumentException("Capital entry");
            points.add(new Entry(id, pos[0], pos[1], pos[2], facing));
        }
        entries = List.copyOf(points); life = root.getAsJsonObject("life").deepCopy();
        lifeLayout = text(life, "layout_id");
        if (!lifeLayout.matches("[a-z0-9_]{1,96}") || life.getAsJsonArray("places").isEmpty() || life.getAsJsonArray("places").size() > 256)
            throw new IllegalArgumentException("Capital life layout");
        Set<String> placeIds = new HashSet<>();
        for (JsonElement item : life.getAsJsonArray("places")) {
            JsonObject place = item.getAsJsonObject(); String id = text(place, "id");
            if (!id.matches("[a-z0-9_]+") || !placeIds.add(id)) throw new IllegalArgumentException("Duplicate capital life place");
            if(!Set.of("rest","work","gather","garden","market","social","entry","cultivate").contains(text(place,"kind")))
                throw new IllegalArgumentException("Capital life place kind");
            localPoint(place.getAsJsonArray("pos")); integer(place, "capacity", 1, 64);
            if (!place.get("block").getAsString().isEmpty()) localPoint(place.getAsJsonArray("block_pos"));
        }
        if(!placeIds.containsAll(Set.of("inn_work","inn_rest","inn_social","guest_west","herbalist_work","herbalist_rest","smith_work","smith_rest")))
            throw new IllegalArgumentException("Capital bundle missing required service slots");
    }

    /** Metadata hash rejects geometry changes but ignores status/provenance-only edits. */
    public void requireManifest(JsonObject root) {
        if (integer(root, "schema", 1, 1) != 1 || !text(root, "format").equals("capital_game_bundle")
                || !text(root, "city_id").equals(cityId) || !text(root, "region_id").equals(regionId)
                || !WorldPlanRules.sha256(identityJson(root)).equals(manifestHash))
            throw new IllegalStateException("Frozen capital manifest differs: " + resource + "; restore matching V8 assets");
    }
    public static String identityJson(JsonObject root) {
        JsonObject projection = new JsonObject();
        for (String key : IDENTITY_KEYS) {
            if (!root.has(key)) throw new IllegalArgumentException("Missing capital field " + key);
            projection.add(key, root.get(key));
        }
        projection.add("placement",pick(root.getAsJsonObject("placement"),"vertical_mode anchor_y rotation_quarters blend_margin"));
        projection.add("terrain",pick(root.getAsJsonObject("terrain"),"resource sha256 encoding width depth top_block fill_block subsurface_block subsurface_depth"));
        projection.add("tiles",pickArray(root.getAsJsonArray("tiles"),"x z template sha256"));
        projection.add("entries",pickArray(root.getAsJsonArray("entries"),"id position facing"));
        JsonObject life=new JsonObject();life.add("layout_id",root.getAsJsonObject("life").get("layout_id"));
        life.add("places",pickArray(root.getAsJsonObject("life").getAsJsonArray("places"),"id kind pos capacity block block_pos"));
        projection.add("life",life);
        return WorldPlanRules.canonical(projection);
    }
    private static JsonObject pick(JsonObject root,String fields) {
        JsonObject selected=new JsonObject();
        for(String key:fields.split(" ")) {
            if(!root.has(key))throw new IllegalArgumentException("Missing capital identity field "+key);
            selected.add(key,root.get(key));
        }
        return selected;
    }
    private static JsonArray pickArray(JsonArray root,String fields) {
        JsonArray selected=new JsonArray();for(var item:root)selected.add(pick(item.getAsJsonObject(),fields));return selected;
    }
    public JsonObject life() { return life.deepCopy(); }
    public int topSolid(int x, int z) { return topSolid[Math.clamp(z, 0, depth - 1) * width + Math.clamp(x, 0, width - 1)]; }
    public Optional<WorldPlanRules.Tile> tile(int x, int z) { return Optional.ofNullable(tileIndex.get(key(x, z))); }
    public Entry entrance() { return entries.stream().filter(e -> e.id().equals("main_gate")).findFirst().orElse(entries.getFirst()); }
    public static String templateFile(String template) { return "myvillage:structure/" + template.substring("myvillage:".length()) + ".nbt"; }
    private int[] localPoint(JsonArray data) {
        int[] p = point(data);
        if (p[0] < 0 || p[0] >= width || p[2] < 0 || p[2] >= depth || p[1] < 1 || p[1] + 1 >= height)
            throw new IllegalArgumentException("Capital entry/life position outside local volume");
        return p;
    }
    private static int[] point(JsonArray array) {
        if (array == null || array.size() != 3) throw new IllegalArgumentException("Capital integer triple");
        int[] p = new int[3];
        for (int i = 0; i < 3; i++) {
            if(!array.get(i).isJsonPrimitive()||!array.get(i).getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("Capital numeric coordinate");
            double n = array.get(i).getAsDouble();
            if (!Double.isFinite(n) || n != StrictMath.rint(n) || n < -30000000 || n > 30000000) throw new IllegalArgumentException("Capital integer coordinate");
            p[i] = (int)n;
        }
        return p;
    }
    private static long key(int x, int z) { return ((long)x << 32) ^ (z & 0xffffffffL); }
    public static byte[] readResource(String id, int maximum) {
        if (!id.matches("myvillage:[a-z0-9_/.-]+") || id.contains("..")) throw new IllegalArgumentException("Capital resource path");
        try (var stream = CapitalGameBundle.class.getResourceAsStream("/data/myvillage/" + id.substring("myvillage:".length()))) {
            if (stream == null) throw new IllegalStateException("Missing frozen capital resource " + id);
            byte[] bytes = stream.readNBytes(maximum + 1);
            if (bytes.length > maximum) throw new IllegalArgumentException("Capital resource too large " + id);
            return bytes;
        } catch (IOException e) { throw new IllegalStateException("Cannot read capital resource " + id, e); }
    }
    public static void requireHash(byte[] bytes, String expected, String name) {
        try {
            String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            if (!actual.equals(expected)) throw new IllegalStateException("Frozen capital asset differs: " + name + "; restore matching V8 resources");
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private static String blockId(JsonObject root, String key) {
        String id = text(root, key);
        if (!id.matches("[a-z0-9_]+:[a-z0-9_/]+")) throw new IllegalArgumentException("Capital ground block ID");
        return id;
    }
    static String text(JsonObject root, String key) {
        JsonElement e = root.get(key);
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString() || e.getAsString().isBlank()) throw new IllegalArgumentException("Capital text " + key);
        return e.getAsString();
    }
    private static String hashText(JsonObject root, String key) {
        String hash = text(root, key);
        if (!hash.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("Capital SHA-256 " + key);
        return hash;
    }
    static int integer(JsonObject root, String key, int low, int high) {
        JsonElement e = root.get(key);
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException("Capital integer " + key);
        double n = e.getAsDouble();
        if (!Double.isFinite(n) || n != StrictMath.rint(n) || n < low || n > high) throw new IllegalArgumentException("Capital bounds " + key);
        return (int)n;
    }
}
