package com.example.myvillage.worldplan;

import com.google.gson.*;
import java.util.*;

/** Frozen V2 climate palette. Adjacent climate bands provide a temperate transition,
 * without random per-block biome speckles or changing regional identities. */
public final class WorldPlanEcology {
    public record Climate(double temperature, double moisture) {
        public Climate mix(Climate other, double t) {
            return new Climate(temperature+(other.temperature-temperature)*t, moisture+(other.moisture-moisture)*t);
        }
    }
    private record MoistureBand(double max, String biome) {}
    private record Band(double max, List<MoistureBand> moisture) {}
    public final double blend, noiseScale, temperatureVariation, moistureVariation, coolingStartY, coolingPerBlock;
    public final boolean clearSpawnCanopy;
    private final Map<String, Climate> profiles;
    private final List<Band> bands;
    private final Set<String> biomes;

    public WorldPlanEcology(JsonObject data, Set<String> profileIds, int cellSize) {
        if (data == null) throw new IllegalArgumentException("V2 requires frozen ecology");
        JsonElement canopy=data.get("clear_spawn_canopy");
        if(canopy!=null&&(!canopy.isJsonPrimitive()||!canopy.getAsJsonPrimitive().isBoolean()))
            throw new IllegalArgumentException("clear_spawn_canopy must be boolean");
        clearSpawnCanopy=canopy!=null&&canopy.getAsBoolean();
        // Same bounded 5x5 outer-cell neighbourhood as the terrain sampler.
        blend = number(data,"blend",64,cellSize/3.0);
        noiseScale = number(data,"noise_scale",128,10000);
        temperatureVariation = number(data,"temperature_variation",0,0.1);
        moistureVariation = number(data,"moisture_variation",0,0.2);
        coolingStartY = number(data,"cooling_start_y",64,256);
        coolingPerBlock = number(data,"cooling_per_block",0,0.01);
        JsonObject ps = data.getAsJsonObject("profiles");
        if (ps == null || !ps.keySet().equals(profileIds)) throw new IllegalArgumentException("Climate profiles must match region profiles");
        Map<String,Climate> climates = new HashMap<>();
        ps.entrySet().forEach(e -> {
            JsonObject p = e.getValue().getAsJsonObject();
            climates.put(e.getKey(),new Climate(number(p,"temperature",0,1),number(p,"moisture",0,1)));
        });
        profiles = Map.copyOf(climates);
        List<Band> parsed = new ArrayList<>(); Set<String> names = new HashSet<>(); double last = -1;
        JsonArray rawBands = data.getAsJsonArray("bands");
        if (rawBands == null || rawBands.isEmpty()) throw new IllegalArgumentException("Missing climate bands");
        for (JsonElement entry : rawBands) {
            JsonObject b = entry.getAsJsonObject(); double max = number(b,"max_temperature",0,2);
            if (max <= last) throw new IllegalArgumentException("Climate bands must be strictly ordered");
            last = max; double previous = -1; List<MoistureBand> ms = new ArrayList<>();
            JsonArray rawMoisture = b.getAsJsonArray("moisture");
            if (rawMoisture == null || rawMoisture.isEmpty()) throw new IllegalArgumentException("Missing moisture bands");
            for (JsonElement m : rawMoisture) {
                JsonObject o = m.getAsJsonObject(); double bound = number(o,"max",0,2);
                if (bound <= previous) throw new IllegalArgumentException("Moisture bands must be strictly ordered");
                previous = bound; String biome = o.get("biome").getAsString();
                if (!biome.matches("minecraft:[a-z0-9_]+")) throw new IllegalArgumentException("Invalid climate biome " + biome);
                names.add(biome); ms.add(new MoistureBand(bound,biome));
            }
            if (previous <= 1) throw new IllegalArgumentException("Moisture bands must cover all clamped values");
            parsed.add(new Band(max,List.copyOf(ms)));
        }
        if (last <= 1) throw new IllegalArgumentException("Temperature bands must cover all clamped values");
        bands = List.copyOf(parsed); biomes = Set.copyOf(names);
    }
    public Climate profile(String id) { return Objects.requireNonNull(profiles.get(id),id); }
    public Set<String> biomes() { return biomes; }
    public String biome(Climate climate) {
        for (Band band : bands) if (climate.temperature < band.max)
            for (MoistureBand m : band.moisture) if (climate.moisture < m.max) return m.biome;
        throw new IllegalArgumentException("Climate must be finite and clamped to [0,1]");
    }
    private static double number(JsonObject o, String key, double min, double max) {
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException("Expected climate number " + key);
        double v = e.getAsDouble();
        if (!Double.isFinite(v) || v < min || v > max) throw new IllegalArgumentException("Invalid climate " + key);
        return v;
    }
}
