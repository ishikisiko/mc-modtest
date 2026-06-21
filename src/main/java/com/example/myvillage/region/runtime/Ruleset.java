package com.example.myvillage.region.runtime;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * The topology rules — all tunables live here, not in code. Java mirror of
 * {@code Ruleset} in {@code tools/buildgen/region_topology.py}. Parsed from the
 * shipped {@code data/myvillage/worldgen/region_topology.json}.
 *
 * @param id                       ruleset id
 * @param regionCount              per-seed region-count range
 * @param tierRange                assignable tier range (anchor holds the top)
 * @param tierStep                 max tier-step along a 连 edge
 * @param separatorPalette         allowed 隔 separators
 * @param peripheralRingRadius     graph-units radius of the peripheral ring
 * @param walledRingRadius         graph-units radius of the walled (outer) ring
 * @param peripheralNearestPeers   k-nearest geometric neighbours per peripheral
 * @param nonTreeEdgeLianChance    probability a non-tree non-walled edge is 连
 * @param walledGateChance         probability a walled region keeps a 关隘
 * @param walledPassLabel          label for a walled region's retained 连 edge
 * @param catalogDir               catalog dir the ruleset's regions come from
 */
public record Ruleset(
        String id,
        IntRange regionCount,
        IntRange tierRange,
        int tierStep,
        List<String> separatorPalette,
        double peripheralRingRadius,
        double walledRingRadius,
        int peripheralNearestPeers,
        double nonTreeEdgeLianChance,
        double walledGateChance,
        String walledPassLabel,
        String catalogDir) {

    /** Parse {@code region_topology.json}. Mirrors Python {@code Ruleset.from_dict}. */
    public static Ruleset fromJson(JsonObject data) {
        List<String> palette = new ArrayList<>();
        for (JsonElement e : data.getAsJsonArray("separator_palette")) {
            String sep = e.getAsString();
            if (!sep.equals(RegionContract.SEP_MOUNTAIN) && !sep.equals(RegionContract.SEP_OCEAN)) {
                throw new IllegalArgumentException(
                        "separator " + sep + " not in palette {特殊山脉, 特殊海洋}");
            }
            palette.add(sep);
        }
        JsonObject embedding = data.has("embedding") && data.get("embedding").isJsonObject()
                ? data.getAsJsonObject("embedding") : new JsonObject();
        JsonObject edgeRules = data.has("edge_rules") && data.get("edge_rules").isJsonObject()
                ? data.getAsJsonObject("edge_rules") : new JsonObject();
        JsonObject roleRules = data.has("role_rules") && data.get("role_rules").isJsonObject()
                ? data.getAsJsonObject("role_rules") : new JsonObject();
        JsonObject walledRule = roleRules.has("walled") && roleRules.get("walled").isJsonObject()
                ? roleRules.getAsJsonObject("walled") : new JsonObject();
        String anchorPlace = embedding.has("anchor_placement")
                ? embedding.get("anchor_placement").getAsString() : "center";
        if (!anchorPlace.equals("center")) {
            throw new IllegalArgumentException(
                    "anchor_placement must be 'center', got " + anchorPlace);
        }
        return new Ruleset(
                data.get("id").getAsString(),
                RegionProfile.asPair(data.getAsJsonArray("region_count")),
                RegionProfile.asPair(data.getAsJsonArray("tier_range")),
                data.get("tier_step").getAsInt(),
                List.copyOf(palette),
                embedding.has("peripheral_ring_radius")
                        ? embedding.get("peripheral_ring_radius").getAsDouble() : 1.0,
                embedding.has("walled_ring_radius")
                        ? embedding.get("walled_ring_radius").getAsDouble() : 1.4,
                embedding.has("peripheral_nearest_peers")
                        ? embedding.get("peripheral_nearest_peers").getAsInt() : 2,
                edgeRules.has("non_tree_edge_lian_chance")
                        ? edgeRules.get("non_tree_edge_lian_chance").getAsDouble() : 0.5,
                edgeRules.has("walled_gate_chance")
                        ? edgeRules.get("walled_gate_chance").getAsDouble() : 0.7,
                walledRule.has("pass_label")
                        ? walledRule.get("pass_label").getAsString() : RegionContract.PASS_GUANAI,
                data.has("catalog")
                        ? data.get("catalog").getAsString()
                        : "data/myvillage/worldgen/region_profile");
    }
}
