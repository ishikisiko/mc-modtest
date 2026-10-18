package com.example.myvillage.sim.life;

import com.example.myvillage.sim.SimDataException;
import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** All cadence, behaviour and supply tuning comes from world_sim/life_rules.json. */
public record LifeRules(int runtimeIntervalTicks, int spawnRadius, int withdrawMargin, int maxSites,
                        int maxResidentsPerSite, int maxPlacesPerSite, int pathRetryTicks, int pathTimeoutTicks,
                        int stuckTicks, int conversationPauseTicks, double arrivalDistance, double walkSpeed,
                        int maxPathDistance, int travelTicksPerBlock, int retryTicks, int maxTransitionsPerTick,
                        int catchUpTicks, double restStartFraction, double restEndFraction, int fatigueThreshold,
                        int fatiguePerActivity, int restRecovery, int injuryRestThreshold, int initialCoins,
                        int initialTreasury, int desiredCoins, int gatherUnits, int gatherQuotaPerDay,
                        int herbPrice, int workWage, int restCost, int cultivationCost, int maxInventory,
                        int socialMemoryLimit, int socialFamiliarityGain, int socialStandingMax,
                        Map<String, Integer> durations) {
    public static final Set<String> ACTIVITIES = Set.of("rest", "work", "gather", "deliver", "cultivate", "social");
    private static final Set<String> FIELDS = Set.of("schema", "runtime_interval_ticks", "spawn_radius", "withdraw_margin",
            "max_sites", "max_residents_per_site", "max_places_per_site", "path_retry_ticks", "path_timeout_ticks",
            "stuck_ticks", "conversation_pause_ticks", "arrival_distance", "walk_speed", "max_path_distance",
            "travel_ticks_per_block", "retry_ticks", "max_transitions_per_tick", "catch_up_ticks", "rest_start_fraction",
            "rest_end_fraction", "fatigue_threshold", "fatigue_per_activity", "rest_recovery", "injury_rest_threshold",
            "initial_coins", "initial_treasury", "desired_coins", "gather_units", "gather_quota_per_day", "herb_price",
            "work_wage", "rest_cost", "cultivation_cost", "max_inventory", "social_memory_limit",
            "social_familiarity_gain", "social_standing_max", "durations");

    public LifeRules {
        durations = Map.copyOf(durations);
    }

    public int duration(String activity) { return durations.get(activity); }

    public static LifeRules parse(String file, JsonObject o) {
        for (String key : o.keySet()) if (!FIELDS.contains(key)) throw new SimDataException(file,key,"unknown field");
        if (integer(file, o, "schema", 1, 1) != 1) throw new SimDataException(file, "schema", "expected 1");
        Map<String, Integer> durations = new LinkedHashMap<>();
        if (!o.has("durations") || !o.get("durations").isJsonObject()) throw new SimDataException(file,"durations","expected object");
        JsonObject d = o.getAsJsonObject("durations");
        for (String key : d.keySet()) if (!ACTIVITIES.contains(key)) throw new SimDataException(file,"durations." + key,"unknown activity");
        for (String a : ACTIVITIES) durations.put(a, integer(file, d, a, 20, 240_000));
        LifeRules r = new LifeRules(
                integer(file,o,"runtime_interval_ticks",1,200), integer(file,o,"spawn_radius",8,128),
                integer(file,o,"withdraw_margin",8,128), integer(file,o,"max_sites",1,256),
                integer(file,o,"max_residents_per_site",1,64), integer(file,o,"max_places_per_site",1,256),
                integer(file,o,"path_retry_ticks",10,1200), integer(file,o,"path_timeout_ticks",40,24_000),
                integer(file,o,"stuck_ticks",20,2400), integer(file,o,"conversation_pause_ticks",1,600),
                decimal(file,o,"arrival_distance",0.25,2), decimal(file,o,"walk_speed",0.1,2),
                integer(file,o,"max_path_distance",8,256), integer(file,o,"travel_ticks_per_block",1,100),
                integer(file,o,"retry_ticks",20,2400), integer(file,o,"max_transitions_per_tick",1,32),
                integer(file,o,"catch_up_ticks",20,24_000), decimal(file,o,"rest_start_fraction",0,1),
                decimal(file,o,"rest_end_fraction",0,1), integer(file,o,"fatigue_threshold",1,100),
                integer(file,o,"fatigue_per_activity",1,100), integer(file,o,"rest_recovery",1,100),
                integer(file,o,"injury_rest_threshold",1,100), integer(file,o,"initial_coins",0,10_000),
                integer(file,o,"initial_treasury",0,1_000_000), integer(file,o,"desired_coins",1,10_000),
                integer(file,o,"gather_units",1,100), integer(file,o,"gather_quota_per_day",1,10_000),
                integer(file,o,"herb_price",1,100), integer(file,o,"work_wage",0,100),
                integer(file,o,"rest_cost",0,100), integer(file,o,"cultivation_cost",0,100),
                integer(file,o,"max_inventory",1,1_000_000), integer(file,o,"social_memory_limit",1,64),
                integer(file,o,"social_familiarity_gain",1,100), integer(file,o,"social_standing_max",1,1000),
                durations);
        if (r.restStartFraction >= r.restEndFraction) throw new SimDataException(file,"rest_end_fraction","must follow rest_start_fraction");
        if (r.initialCoins > r.maxInventory || r.initialTreasury > r.maxInventory) throw new SimDataException(file,"max_inventory","below initial balance");
        return r;
    }

    private static int integer(String file, JsonObject o, String key, int min, int max) {
        try {
            var e = o.get(key);
            if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException();
            int n = e.getAsBigDecimal().intValueExact();
            if (n < min || n > max) throw new IllegalArgumentException();
            return n;
        } catch (RuntimeException e) { throw new SimDataException(file,key,"expected integer " + min + ".." + max); }
    }

    private static double decimal(String file, JsonObject o, String key, double min, double max) {
        try {
            var e = o.get(key);
            if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException();
            double n = e.getAsDouble();
            if (!Double.isFinite(n) || n < min || n > max) throw new IllegalArgumentException();
            return n;
        } catch (RuntimeException e) { throw new SimDataException(file,key,"expected number " + min + ".." + max); }
    }
}
