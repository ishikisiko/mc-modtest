package com.example.myvillage.region.runtime;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * An authored region (洲) identity — the catalog entry. Java mirror of
 * {@code RegionProfile} in {@code tools/buildgen/region_topology.py}.
 *
 * <p>{@code tier} is the <em>nominal</em> (catalog) tier; the generator assigns
 * each region a concrete <em>assigned tier</em> outward from the anchor and
 * records it on {@link GenRegion}.
 *
 * @param id                stable region id (e.g. {@code "zhongzhou"})
 * @param displayName       human glyph name (e.g. {@code "中州"})
 * @param tier              nominal (catalog) tier
 * @param qi                qi range
 * @param danger            danger range
 * @param role              {@code "anchor"} | {@code "peripheral"} | {@code "walled"}
 * @param admittedSubjects  subjects this region admits (today {@code "sect"})
 */
public record RegionProfile(
        String id,
        String displayName,
        int tier,
        IntRange qi,
        IntRange danger,
        String role,
        List<String> admittedSubjects) {

    /** Parse a {@code region_profile/*.json} entry. */
    public static RegionProfile fromJson(JsonObject data) {
        String role = data.get("role").getAsString();
        if (!role.equals("anchor") && !role.equals("peripheral") && !role.equals("walled")) {
            throw new IllegalArgumentException(
                    "region " + strOr(data, "id", "?") + ": unknown role " + role);
        }
        return new RegionProfile(
                data.get("id").getAsString(),
                data.get("display_name").getAsString(),
                data.get("tier").getAsInt(),
                asPair(data.getAsJsonArray("qi")),
                asPair(data.getAsJsonArray("danger")),
                role,
                asStringList(data, "admitted_subjects"));
    }

    static IntRange asPair(JsonArray value) {
        if (value.size() != 2 || value.get(0).getAsInt() > value.get(1).getAsInt()) {
            throw new IllegalArgumentException(
                    "expected a [lo, hi] pair with lo <= hi, got " + value);
        }
        return new IntRange(value.get(0).getAsInt(), value.get(1).getAsInt());
    }

    private static List<String> asStringList(JsonObject data, String field) {
        List<String> out = new ArrayList<>();
        if (data.has(field) && data.get(field).isJsonArray()) {
            for (JsonElement e : data.getAsJsonArray(field)) {
                out.add(e.getAsString());
            }
        }
        return List.copyOf(out);
    }

    private static String strOr(JsonObject data, String field, String fallback) {
        return data.has(field) && data.get(field).isJsonPrimitive()
                ? data.get(field).getAsString() : fallback;
    }
}
