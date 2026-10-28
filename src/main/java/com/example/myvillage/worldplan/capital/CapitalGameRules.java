package com.example.myvillage.worldplan.capital;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.example.myvillage.sim.SimData;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Compact frozen references keep the multi-megabyte heightfields and sparse NBT out of level.dat. */
public final class CapitalGameRules {
    public final int ringMin, ringMax, attempts, searchRadius, structureBuffer, minY, maxY, lifePlaceLimit;
    public final List<CapitalGameBundle> bundles;
    public CapitalGameRules(JsonObject root) {
        this(root, false);
    }
    public CapitalGameRules(JsonObject root, boolean versionedAssets) {
        if (root == null || CapitalGameBundle.integer(root, "schema", 1, 1) != 1
                || !CapitalGameBundle.text(root, "on_candidate_exhaustion").equals("fail_whole_plan"))
            throw new IllegalArgumentException("Missing V8 capital rules");
        ringMin = CapitalGameBundle.integer(root, "ring_min", 4000, 8000);
        ringMax = CapitalGameBundle.integer(root, "ring_max", ringMin, 8500);
        attempts = CapitalGameBundle.integer(root, "placement_attempts", 1, 256);
        searchRadius = CapitalGameBundle.integer(root, "placement_radius", 0, 2048);
        structureBuffer = CapitalGameBundle.integer(root, "structure_buffer", 16, 256);
        lifePlaceLimit = CapitalGameBundle.integer(root, "life_places_limit", 1, 256);
        minY = CapitalGameBundle.integer(root, "minimum_y", -64, -64);
        maxY = CapitalGameBundle.integer(root, "maximum_y_exclusive", 320, 320);
        var input = root.getAsJsonArray("bundles");
        if (input.size() != CapitalRegion.values().length) throw new IllegalArgumentException("V8 requires exactly eight capitals including Moyu");
        Map<String, JsonObject> assets = versionedAssets ? assetDefinitions(root.getAsJsonObject("assets")) : Map.of();
        Set<String> ids = new HashSet<>(), regions = new HashSet<>(); List<CapitalGameBundle> list = new ArrayList<>();
        for (var item : input) {
            var bundle = new CapitalGameBundle(item.getAsJsonObject());
            if (versionedAssets) {
                JsonObject definition = assets.get(bundle.cityId);
                if (definition == null || !bundle.regionId.equals(CapitalGameBundle.text(definition, "region_id"))
                        || !bundle.resourceVersion.equals(CapitalGameBundle.text(definition, "resource_version"))
                        || !bundle.layoutFamily.equals(CapitalGameBundle.text(definition, "layout_family_id"))
                        || !bundle.lifeLayout.equals(CapitalGameBundle.text(definition, "life_layout_id")))
                    throw new IllegalArgumentException("主城资源与该世界冻结的版本、布局族或生活身份不符");
            } else if (!bundle.resourceVersion.equals("v1")) {
                throw new IllegalArgumentException("旧 V8 世界必须继续读取 v1 主城资源");
            }
            if(bundle.life().getAsJsonArray("places").size()>lifePlaceLimit)throw new IllegalArgumentException("Capital life layout exceeds frozen V8 budget");
            if (!ids.add(bundle.cityId) || !regions.add(bundle.regionId)) throw new IllegalArgumentException("Duplicate capital identity");
            list.add(bundle);
        }
        list.sort(Comparator.comparing(b -> b.cityId)); bundles = List.copyOf(list);
    }
    private static Map<String, JsonObject> assetDefinitions(JsonObject root) {
        if (root == null) throw new IllegalArgumentException("V9 缺少冻结的主城资源版本配置");
        var inputVersions = root.getAsJsonArray("accepted_versions");
        if (inputVersions == null || inputVersions.isEmpty() || inputVersions.size() > 2)
            throw new IllegalArgumentException("V9 资源版本白名单不合法");
        Set<String> versions = new HashSet<>();
        for (var item : inputVersions) {
            if (!item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString()
                    || !Set.of("v1", "v2").contains(item.getAsString()) || !versions.add(item.getAsString()))
                throw new IllegalArgumentException("V9 仅允许显式 v1/v2 资源版本");
        }
        var input = root.getAsJsonArray("cities");
        if (input == null || input.size() != CapitalRegion.values().length)
            throw new IllegalArgumentException("V9 资源配置必须包含八座主城");
        Map<String, JsonObject> result = new HashMap<>(); Set<String> regions = new HashSet<>();
        for (var item : input) {
            JsonObject city = item.getAsJsonObject();
            String id = CapitalGameBundle.text(city, "city_id"), region = CapitalGameBundle.text(city, "region_id");
            CapitalRegion.fromId(region);
            if (!id.equals("capital_" + region) || result.putIfAbsent(id, city) != null || !regions.add(region)
                    || !versions.contains(CapitalGameBundle.text(city, "resource_version")))
                throw new IllegalArgumentException("V9 资源配置的城 ID、州域或版本不合法");
            CapitalGameBundle.text(city, "layout_family_id"); CapitalGameBundle.text(city, "life_layout_id");
        }
        return Map.copyOf(result);
    }
    /** The existing LifeRules parser/codec enforce the frozen V8/V9 budget on creation AND restore. */
    public SimData.ResourceOpener lifeOpener(SimData.ResourceOpener base) {
        return path -> {
            if(!path.equals(SimData.DIRECTORY+"life_rules.json"))return base.open(path);
            try(var input=base.open(path)) {
                if(input==null)return null;
                JsonObject root=JsonParser.parseReader(new InputStreamReader(input,StandardCharsets.UTF_8)).getAsJsonObject();
                root.addProperty("max_places_per_site",lifePlaceLimit);
                return new ByteArrayInputStream(root.toString().getBytes(StandardCharsets.UTF_8));
            }
        };
    }
}
