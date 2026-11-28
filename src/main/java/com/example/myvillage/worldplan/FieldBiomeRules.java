package com.example.myvillage.worldplan;

import com.google.gson.JsonObject;
import java.util.List;
import java.util.Map;

import static com.example.myvillage.worldplan.WorldPlanRules.integer;
import static com.example.myvillage.worldplan.WorldPlanRules.number;
import static com.example.myvillage.worldplan.WorldPlanRules.text;

/** Data that determines the provincial surface fields and the range of an encounter. */
public final class FieldBiomeRules {
    public enum Kind { TIDE, WIND }

    public record Domain(Kind kind, String region, String biome, double patchScale, double patchEdge,
                         double patchBlend, double biomeWeight, int surveyRadius, int clearance, int arenaWidth, int arenaDepth,
                         int dryArea, int wakeRadius, int observerRadius, int maximumRise) {}

    public record Tide(Domain domain, int shorePeriod, int baySpacing, int coastWidth,
                       int channelCut, int channelSpread, int channelWidth, int rockLift, int maximumBiomeDepth) {}

    public record Wind(Domain domain, int valleySpacing, int fractureSpacing, int valleyWidth,
                       int plateauLift, int heightAboveSea, int bridgeSpan, int fractureDepth) {}

    public final Tide tide;
    public final Wind wind;
    public final List<Domain> domains;
    public final int foundationClearance;
    public final int foundationBlend;
    private final Map<String, Domain> byRegion;

    public FieldBiomeRules(JsonObject source) {
        foundationClearance = integer(source, "foundation_clearance", 4, 24);
        foundationBlend = integer(source, "foundation_blend", 24, 96);
        var coast = source.getAsJsonObject("tide");
        var desert = source.getAsJsonObject("wind");
        tide = new Tide(domain(Kind.TIDE, coast),
                integer(coast, "shore_period", 800, 2400), integer(coast, "bay_spacing", 320, 800),
                integer(coast, "coast_width", 180, 350), integer(coast, "channel_cut", 10, 24),
                integer(coast, "channel_spread", 32, 55), integer(coast, "channel_width", 8, 20),
                integer(coast, "rock_lift", 20, 75),
                integer(coast, "maximum_biome_depth", 10, 32));
        wind = new Wind(domain(Kind.WIND, desert),
                integer(desert, "valley_spacing", 350, 800), integer(desert, "fracture_spacing", 300, 800),
                integer(desert, "valley_width", 85, 140), integer(desert, "plateau_lift", 40, 105),
                integer(desert, "height_above_sea", 8, 50), integer(desert, "bridge_span", 12, 14),
                integer(desert, "fracture_depth", 9, 18));
        domains = List.of(tide.domain, wind.domain);
        byRegion = Map.of(tide.domain.region, tide.domain, wind.domain.region, wind.domain);
        if (tide.domain.biome.equals(wind.domain.biome))
            throw new IllegalArgumentException("野外群系标识重复");
    }

    public Domain region(String id) { return byRegion.get(id); }

    private static Domain domain(Kind kind, JsonObject source) {
        String biome = text(source, "biome");
        if (!biome.matches("yuanxian:[a-z_]+")) throw new IllegalArgumentException("野外群系标识无效 " + biome);
        return new Domain(kind, text(source, "region"), biome,
                number(source, "patch_scale", 640, 4000), number(source, "patch_edge", -.8, .2),
                number(source, "patch_blend", .1, .8), number(source, "biome_weight", .6, .98),
                integer(source, "survey_radius", 750, 10000),
                integer(source, "clearance", 8, 112), integer(source, "arena_width", 192, 512),
                integer(source, "arena_depth", 160, 448), integer(source, "dry_area", 4000, 95000),
                integer(source, "wake_radius", 24, 128), integer(source, "observer_radius", 40, 160),
                integer(source, "maximum_rise", 0, 14));
    }
}
