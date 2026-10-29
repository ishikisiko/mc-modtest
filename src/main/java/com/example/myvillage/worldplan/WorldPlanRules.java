package com.example.myvillage.worldplan;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Validated, frozen generation inputs. Runtime code never rereads mutable configuration per chunk. */
public final class WorldPlanRules {
    public static final String LEGACY_ALGORITHM = "myvillage:worldplan_v1";
    public static final String V2_ALGORITHM = "myvillage:worldplan_v2";
    public static final String V3_ALGORITHM = "myvillage:worldplan_v3";
    public static final String ALGORITHM = "myvillage:worldplan_v4";
    public static final String V7_ALGORITHM = "myvillage:worldplan_v7";
    public static final String V7_RESOURCE = "/data/myvillage/worldplan/frozen_v7.json";
    public static final String V8_ALGORITHM = "myvillage:worldplan_v8";
    public static final String V8_RESOURCE = "/data/myvillage/worldplan/frozen_v8.json";
    public static final String V9_ALGORITHM = "myvillage:worldplan_v9";
    public static final String V9_RESOURCE = "/data/myvillage/worldplan/frozen_v9.json";
    public static final String V10_ALGORITHM = "myvillage:worldplan_v10";
    public static final String V10_RESOURCE = "/data/myvillage/worldplan/frozen_v10.json";
    public static final String V11_ALGORITHM = "myvillage:worldplan_v11";
    public static final String V11_RESOURCE = "/data/myvillage/worldplan/frozen_v11.json";
    public static final String V6_ALGORITHM = "myvillage:worldplan_v6";
    public static final String V6_RESOURCE = "/data/myvillage/worldplan/frozen_v6.json";
    public static final String V5_ALGORITHM = "myvillage:worldplan_v5";
    public static final String V5_RESOURCE = "/data/myvillage/worldplan/frozen_v5.json";
    public static final String LEGACY_RESOURCE = "/data/myvillage/worldplan/frozen_v1.json";
    public static final String V2_RESOURCE = "/data/myvillage/worldplan/frozen_v2.json";
    public static final String V3_RESOURCE = "/data/myvillage/worldplan/frozen_v3.json";
    public static final String RESOURCE = V11_RESOURCE;
    public static final Gson JSON = new GsonBuilder().disableHtmlEscaping().create();
    public record Profile(String id, String name, String role, int nominalTier, int qiMin, int qiMax,
                          int dangerMin, int dangerMax, double base, double amplitude,
                          List<String> biomes, int outerWeight) {
        public double qi() { return (qiMin + qiMax) / 2.0; }
        public double danger() { return (dangerMin + dangerMax) / 2.0; }
    }
    public record Tile(String template, int x, int z, String sha256) {}
    public record StructurePolicy(boolean allowOuter, boolean allowWalled, double minimumQi, int minimumTier, int maximumTier) {}
    public record CityRules(String id, String name, String host, int baseY, int width, int height, int depth,
                            int placementRadius, int placementAttempts, int skirt, int margin, int structureBuffer,
                            int[] terraceEnds, int[] terraceHeights, List<Tile> tiles, String layoutHash, int[] surfaces, String lifeLayout) {
        public CityRules {
            terraceEnds = terraceEnds.clone(); terraceHeights = terraceHeights.clone(); tiles = List.copyOf(tiles); surfaces = surfaces.clone();
        }
        @Override public int[] terraceEnds() { return terraceEnds.clone(); }
        @Override public int[] terraceHeights() { return terraceHeights.clone(); }
        @Override public int[] surfaces() { return surfaces.clone(); }
        /** First air above solid terrain. V4 preserves full two-dimensional D1 terraces. */
        public int surface(int x, int z) {
            if (surfaces.length == 0) return terrace(z) + 1;
            return surfaces[Math.clamp(z, 0, depth - 1) * width + Math.clamp(x, 0, width - 1)];
        }
        public int terrace(int localZ) {
            for (int i = 0; i < terraceEnds.length; i++) if (localZ <= terraceEnds[i]) return terraceHeights[i];
            return terraceHeights[terraceHeights.length - 1];
        }
    }
    public final int coreRadius, coreBlend, minCount, maxCount, tierMin, tierMax, stepMin, stepMax;
    public final int seaLevel, minHeight, maxHeight, surfaceSeal, outerCellSize;
    public final int spawnSearchRadius, spawnAttempts, spawnPadRadius, spawnSkirt, spawnMinimumY;
    public final double anchorJitter, innerMin, innerMax, outerMin, outerMax, angularJitter;
    public final double extraRouteChance, walledPassChance, seaSeparatorChance;
    public final double warpScale, warpAmplitude, terrainBlend, boundaryWidth, ridgeLift, channelDepth, passWidth;
    public final double outerJitter, terrainScale, detailScale, detailAmplitude, qiVariation;
    public final List<Profile> profiles;
    public final CityRules city;
    public final com.example.myvillage.worldplan.capital.CapitalGameRules capitals;
    public final SettlementRules settlements;
    public final QianzhenRegionRules qianzhen;
    public final Map<String, StructurePolicy> structurePolicies;
    public final String algorithm;
    public final WorldPlanEcology ecology;
    public final WorldPlanEcotone ecotone;
    private final String canonicalJson, fingerprint;

    public WorldPlanRules(String json) {
        JsonObject r = JsonParser.parseString(json).getAsJsonObject();
        algorithm = text(r, "algorithm");
        if (integer(r, "schema", 1, 1) != 1 || !V11_ALGORITHM.equals(algorithm))
            throw new IllegalArgumentException("当前开发包仅支持当前世界生成版本");
        coreRadius = integer(r, "core_radius", 2000, 32000);
        coreBlend = integer(r, "core_blend", 64, coreRadius / 3);
        int[] count = pair(r, "region_count", 5, 8); minCount = count[0]; maxCount = count[1];
        int[] tier = pair(r, "tier_range", 1, 100); tierMin = tier[0]; tierMax = tier[1];
        int[] step = pair(r, "tier_step", 1, 20); stepMin = step[0]; stepMax = step[1];
        if (tierMin >= tierMax || stepMax >= tierMax - tierMin) throw new IllegalArgumentException("Invalid tier gradient");
        anchorJitter = number(r, "anchor_jitter", 0, coreRadius / 4.0);
        double[] in = doublePair(r, "inner_radius", 100, coreRadius); innerMin = in[0]; innerMax = in[1];
        double[] out = doublePair(r, "outer_radius", 100, coreRadius); outerMin = out[0]; outerMax = out[1];
        if (innerMax + anchorJitter >= outerMin || outerMax >= coreRadius - coreBlend)
            throw new IllegalArgumentException("Region rings must be separated and inside the core");
        angularJitter = number(r, "angular_jitter", 0, 0.35);
        extraRouteChance = number(r, "extra_route_chance", 0, 1);
        walledPassChance = number(r, "walled_pass_chance", 0, 1);
        seaSeparatorChance = number(r, "sea_separator_chance", 0, 1);
        warpScale = number(r, "warp_scale", 64, 100000);
        warpAmplitude = number(r, "warp_amplitude", 0, warpScale / 6.0 - 0.001);
        terrainBlend = number(r, "terrain_blend", 32, coreRadius / 2.0);
        boundaryWidth = number(r, "boundary_width", 32, 1000);
        ridgeLift = number(r, "ridge_lift", 0, 160);
        channelDepth = number(r, "channel_depth", 1, 60);
        passWidth = number(r, "pass_width", 16, boundaryWidth * 2);
        outerCellSize = integer(r, "outer_cell_size", 1024, 16000);
        outerJitter = number(r, "outer_jitter", 0, 0.2);
        if (terrainBlend > outerCellSize / 3.0) throw new IllegalArgumentException("Outer blend exceeds bounded neighbourhood proof");
        terrainScale = number(r, "terrain_scale", 64, 10000);
        detailScale = number(r, "detail_scale", 16, terrainScale);
        detailAmplitude = number(r, "detail_amplitude", 0, 16);
        qiVariation = number(r, "qi_variation", 0, 3);
        seaLevel = integer(r, "sea_level", 32, 96);
        minHeight = integer(r, "min_height", -32, seaLevel - 1);
        maxHeight = integer(r, "max_height", seaLevel + 32, 270);
        surfaceSeal = integer(r, "surface_seal", 3, 24);
        JsonObject spawn = r.getAsJsonObject("spawn");
        spawnSearchRadius = integer(spawn, "search_radius", 128, coreRadius/2);
        spawnAttempts = integer(spawn, "attempts", 16, 512);
        spawnPadRadius = integer(spawn, "pad_radius", 3, 16);
        spawnSkirt = integer(spawn, "pad_skirt", 8, 128);
        spawnMinimumY = integer(spawn, "minimum_y", seaLevel+4, maxHeight-16);
        List<Profile> ps = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (JsonElement item : r.getAsJsonArray("profiles")) {
            JsonObject p = item.getAsJsonObject();
            String id = text(p, "id"), role = text(p, "role");
            if (!id.matches("[a-z0-9_]+") || !ids.add(id) || !Set.of("anchor", "peripheral", "walled").contains(role))
                throw new IllegalArgumentException("Invalid/duplicate region " + id);
            int[] qi = pair(p, "qi", 0, 20), danger = pair(p, "danger", 0, 20);
            List<String> biomes = new ArrayList<>();
            for (JsonElement b : p.getAsJsonArray("biomes")) {
                String value = b.getAsString();
                if (!value.matches("minecraft:[a-z0-9_]+")) throw new IllegalArgumentException("Unsupported biome " + value);
                biomes.add(value);
            }
            if (biomes.size() != 3) throw new IllegalArgumentException("Exactly three terrain variants required: " + id);
            ps.add(new Profile(id, text(p, "display_name"), role, integer(p, "tier", tierMin, tierMax),
                    qi[0], qi[1], danger[0], danger[1], number(p, "base", minHeight, maxHeight),
                    number(p, "amplitude", 0, 160), List.copyOf(biomes), integer(p, "outer_weight", 1, 100)));
        }
        ps.sort(Comparator.comparing(Profile::id));
        profiles = List.copyOf(ps);
        if (ps.size() < maxCount || ps.stream().filter(p -> p.role.equals("anchor")).count() != 1
                || ps.stream().filter(p -> p.role.equals("walled")).count() != 1)
            throw new IllegalArgumentException("Catalog needs one anchor, one walled region and enough peripherals");
        if (minCount != 8 || maxCount != 8 || ps.size() != 8 || r.has("city"))
            throw new IllegalArgumentException("当前主城规划需要八个地区");
        capitals = new com.example.myvillage.worldplan.capital.CapitalGameRules(r.getAsJsonObject("capitals"));
        for (var bundle : capitals.bundles) profile(bundle.regionId);
        if (capitals.ringMax + capitals.searchRadius + 2048 >= coreRadius - coreBlend)
            throw new IllegalArgumentException("主城环带需要完整预留与地形融合空间");
        city = null;
        Map<String, StructurePolicy> policies = new TreeMap<>();
        JsonObject policyData = r.getAsJsonObject("structure_policy");
        for (String id : policyData.keySet()) {
            if (!id.matches("[a-z0-9_]+:[a-z0-9_/]+")) throw new IllegalArgumentException("Invalid structure policy ID " + id);
            JsonObject p = policyData.getAsJsonObject(id);
            policies.put(id, new StructurePolicy(p.get("allow_outer").getAsBoolean(), p.get("allow_walled").getAsBoolean(),
                    number(p, "minimum_qi", 0, 20), integer(p, "minimum_tier", tierMin, tierMax), integer(p, "maximum_tier", tierMin, tierMax)));
            if (policies.get(id).minimumTier() > policies.get(id).maximumTier()) throw new IllegalArgumentException("Reversed policy tier " + id);
        }
        structurePolicies = Map.copyOf(policies);
        ecology = null;
        Set<String> originalBiomes=new HashSet<>(Set.of("minecraft:deep_ocean","minecraft:ocean","minecraft:beach"));
        profiles.forEach(p->originalBiomes.addAll(p.biomes()));
        ecotone = new WorldPlanEcotone(r.getAsJsonObject("ecotone"),originalBiomes);
        settlements = new SettlementRules(r.getAsJsonObject("settlements"));
        qianzhen = null;
        canonicalJson = JSON.toJson(sorted(r));
        fingerprint = sha256(canonicalJson);
    }
    public static boolean compressed(String algorithm) { return V11_ALGORITHM.equals(algorithm); }
    public Profile profile(String id) {
        return profiles.stream().filter(p -> p.id.equals(id)).findFirst().orElseThrow(() -> new IllegalArgumentException("No profile " + id));
    }
    public String json() { return canonicalJson; }
    public String fingerprint() { return fingerprint; }
    public static String canonical(JsonElement value) { return JSON.toJson(sorted(value)); }
    public boolean clearSpawnCanopy() { return ecotone!=null ? ecotone.clearSpawnCanopy : ecology!=null&&ecology.clearSpawnCanopy; }
    public static String sha256(String input) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private static JsonElement sorted(JsonElement e) {
        if (e.isJsonObject()) {
            JsonObject o = new JsonObject(); new TreeSet<>(e.getAsJsonObject().keySet()).forEach(k -> o.add(k, sorted(e.getAsJsonObject().get(k)))); return o;
        }
        if (e.isJsonArray()) { JsonArray a = new JsonArray(); e.getAsJsonArray().forEach(v -> a.add(sorted(v))); return a; }
        return e;
    }
    private static String text(JsonObject o, String key) {
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString() || e.getAsString().isBlank())
            throw new IllegalArgumentException("Expected string: " + key);
        return e.getAsString();
    }
    private static double number(JsonObject o, String key, double lo, double hi) {
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException("Expected number: " + key);
        double n = e.getAsDouble();
        if (!Double.isFinite(n) || n < lo || n > hi) throw new IllegalArgumentException("Out of bounds " + key);
        return n;
    }
    private static int integer(JsonObject o, String key, int lo, int hi) { return boundedInt(o.get(key), lo, hi, key); }
    private static int boundedInt(JsonElement e, int lo, int hi, String key) {
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException("Expected integer " + key);
        double n = e.getAsDouble();
        if (!Double.isFinite(n) || n != StrictMath.rint(n) || n < lo || n > hi) throw new IllegalArgumentException("Out of bounds " + key);
        return (int)n;
    }
    private static int[] pair(JsonObject o, String key, int lo, int hi) {
        JsonArray a = o.getAsJsonArray(key);
        if (a == null || a.size() != 2) throw new IllegalArgumentException("Expected pair " + key);
        int x = boundedInt(a.get(0), lo, hi, key), y = boundedInt(a.get(1), lo, hi, key);
        if (x > y) throw new IllegalArgumentException("Reversed pair " + key); return new int[] {x, y};
    }
    private static double[] doublePair(JsonObject o, String key, double lo, double hi) {
        JsonArray a = o.getAsJsonArray(key);
        if (a == null || a.size() != 2) throw new IllegalArgumentException("Expected pair " + key);
        double x = a.get(0).getAsDouble(), y = a.get(1).getAsDouble();
        if (!Double.isFinite(x) || !Double.isFinite(y) || x < lo || y > hi || x > y) throw new IllegalArgumentException("Invalid pair " + key);
        return new double[] {x, y};
    }
}
