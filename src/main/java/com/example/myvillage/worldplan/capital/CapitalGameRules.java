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
        Set<String> ids = new HashSet<>(), regions = new HashSet<>(); List<CapitalGameBundle> list = new ArrayList<>();
        for (var item : input) {
            var bundle = new CapitalGameBundle(item.getAsJsonObject());
            if(bundle.life().getAsJsonArray("places").size()>lifePlaceLimit)throw new IllegalArgumentException("Capital life layout exceeds frozen V8 budget");
            if (!ids.add(bundle.cityId) || !regions.add(bundle.regionId)) throw new IllegalArgumentException("Duplicate capital identity");
            list.add(bundle);
        }
        list.sort(Comparator.comparing(b -> b.cityId)); bundles = List.copyOf(list);
    }
    /** The existing LifeRules parser/codec enforce this frozen, V8-only world budget on creation AND restore. */
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
