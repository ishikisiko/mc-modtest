package com.example.myvillage.worldplan;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.example.myvillage.worldplan.WorldPlanRules.integer;
import static com.example.myvillage.worldplan.WorldPlanRules.number;
import static com.example.myvillage.worldplan.WorldPlanRules.text;

/** The same authored geography is frozen into each of the three continent presets. */
public final class ProvincialBiomeRules {
    public enum Form { CHANNEL, HOLLOW, TERRACE, RIDGE, SHELF }
    public enum Fluid { NONE, WATER, ICE, SEA, LAVA }

    public record Terrain(Form form, int relief, int cut, int spacing, Fluid fluid, double basinMoisture, List<String> palette) {}
    public record Profile(String biome, String region, int width, int depth, int altitude,
                          double ruggedness, Terrain terrain) {}

    public final double vanillaWeight;
    public final int surveyStep;
    public final List<Profile> profiles;
    private final Map<String, List<Profile>> regions;

    ProvincialBiomeRules(JsonObject source, FieldBiomeRules encounters) {
        integer(source, "schema", 1, 1);
        vanillaWeight = number(source, "vanilla_weight", .2, .95);
        surveyStep = integer(source, "survey_step", 64, 192);
        Map<String, Profile> ids = new LinkedHashMap<>();
        Map<String, List<Profile>> groups = new LinkedHashMap<>();
        for (var value : source.getAsJsonArray("biomes")) {
            var row = value.getAsJsonObject();
            String biome = text(row, "biome"), region = text(row, "region");
            var window = row.getAsJsonArray("window");
            var site = row.getAsJsonObject("site");
            var t = row.getAsJsonObject("terrain");
            var palette = t.getAsJsonArray("palette").asList().stream().map(item -> item.getAsString()).toList();
            if (palette.size() != 4 || palette.stream().anyMatch(id -> !id.matches("minecraft:[a-z_0-9]+")))
                throw new IllegalArgumentException("群系地表材质无效 " + biome);
            var profile = new Profile(biome, region, dimension(window, 0), dimension(window, 1),
                    integer(site, "height", -60, 160), number(site, "rugged", 0, 1),
                    new Terrain(Form.valueOf(text(t, "form").toUpperCase(Locale.ROOT)),
                            integer(t, "relief", 1, 80), integer(t, "cut", 1, 64), integer(t, "spacing", 72, 512),
                            Fluid.valueOf(text(t, "fluid").toUpperCase(Locale.ROOT)),
                            t.has("basin_moisture") ? number(t, "basin_moisture", -.2, .6) : 0, palette));
            if (!biome.matches("yuanxian:[a-z_0-9]+") || ids.putIfAbsent(biome, profile) != null
                    || encounters.domains.stream().anyMatch(domain -> biome.equals(domain.biome())))
                throw new IllegalArgumentException("重复或无效的州域群系 " + biome);
            groups.computeIfAbsent(region, ignored -> new ArrayList<>()).add(profile);
        }
        if (groups.size() != 8 || ids.size() + encounters.domains.size() != 16)
            throw new IllegalArgumentException("八州需要十六组野外群系");
        for (var group : groups.entrySet()) {
            int encounterCount = encounters.region(group.getKey()) == null ? 0 : 1;
            if (group.getValue().size() + encounterCount != 2)
                throw new IllegalArgumentException("州域需要两组野外群系 " + group.getKey());
            group.setValue(List.copyOf(group.getValue()));
        }
        profiles = List.copyOf(ids.values());
        regions = Map.copyOf(groups);
    }

    private static int dimension(JsonArray window, int index) {
        int value = window.get(index).getAsInt();
        if (window.size() != 2 || value < 850 || value > 2600)
            throw new IllegalArgumentException("州域群系采样宽度无效 " + window);
        return value;
    }

    public List<Profile> in(String region) { return regions.getOrDefault(region, List.of()); }
}
