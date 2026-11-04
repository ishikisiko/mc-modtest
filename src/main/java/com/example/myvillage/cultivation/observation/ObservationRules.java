package com.example.myvillage.cultivation.observation;

import com.example.myvillage.cultivation.CultivationProfile;
import com.example.myvillage.stats.RealmKey;
import com.example.myvillage.stats.StatsRules;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/** Shared eligibility and precision rules; a coarse reading contains only its published endpoints. */
public final class ObservationRules {
    public static final ResourceLocation ID = ResourceLocation.parse("myvillage:observation");
    public static final String PATH = "data/myvillage/cultivation/observation.json";
    public record Reading(boolean exact, RealmKey minimum, RealmKey maximum) { }
    private final StatsRules stats;
    private final List<RealmKey> ladder;
    private final RealmKey unlock;
    private final String name;
    private final int exactDistance, coarseStages, cooldownTicks, requestIntervalTicks;
    private final double range;

    private ObservationRules(JsonObject root, StatsRules stats) {
        if (!root.keySet().equals(Set.of("schema", "name", "unlock", "range", "cooldown_ticks",
                "request_interval_ticks", "exact_stage_distance", "coarse_stage_count")))
            throw new IllegalArgumentException("Invalid observation fields");
        integer(root, "schema", 1, 1);
        this.stats = stats;
        var keys = new ArrayList<RealmKey>();
        stats.realms().forEach(realm -> {
            for (int stage = 0; stage < realm.stages(); stage++) keys.add(stats.realmKey(realm.id(), stage));
        });
        ladder = List.copyOf(keys);
        var endpoint = root.getAsJsonObject("unlock");
        if (!endpoint.keySet().equals(Set.of("realm", "stage"))) throw new IllegalArgumentException("Invalid observation unlock");
        unlock = stats.realmKey(text(endpoint, "realm"), integer(endpoint, "stage", 0, 255));
        if (stats.definition(unlock).mortal()) throw new IllegalArgumentException("Observation requires cultivation");
        name = text(root, "name");
        if (name.isBlank() || name.length() > 32) throw new IllegalArgumentException("Invalid observation name");
        var distance = root.get("range");
        if (!distance.isJsonPrimitive() || !distance.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException("Observation range must be numeric");
        range = distance.getAsDouble();
        if (!Double.isFinite(range) || range < 1 || range > 64) throw new IllegalArgumentException("Invalid observation range");
        cooldownTicks = integer(root, "cooldown_ticks", 1, 1200);
        requestIntervalTicks = integer(root, "request_interval_ticks", 1, cooldownTicks);
        exactDistance = integer(root, "exact_stage_distance", 0, 9);
        coarseStages = integer(root, "coarse_stage_count", 2, 9);
    }

    public static ObservationRules parse(String json, StatsRules stats) {
        return new ObservationRules(JsonParser.parseString(json).getAsJsonObject(), stats);
    }
    private static class Holder {
        private static final ObservationRules INSTANCE = load();
    }
    private static ObservationRules load() {
        try (var input = ObservationRules.class.getResourceAsStream("/" + PATH)) {
            if (input == null) throw new IllegalArgumentException("Missing " + PATH);
            return parse(new String(input.readAllBytes(), StandardCharsets.UTF_8), StatsRules.bundled());
        } catch (Exception failure) { throw new IllegalStateException("Cannot load " + PATH, failure); }
    }
    public static ObservationRules bundled() { return Holder.INSTANCE; }
    public String name() { return name; }
    public RealmKey unlock() { return unlock; }
    public double range() { return range; }
    public int cooldownTicks() { return cooldownTicks; }
    public int requestIntervalTicks() { return requestIntervalTicks; }
    public int exactDistance() { return exactDistance; }
    public int coarseStages() { return coarseStages; }
    public boolean unlocked(RealmKey observer) { return rank(observer) >= rank(unlock); }
    public boolean unlocked(CultivationProfile profile) { return profileRealm(profile).map(this::unlocked).orElse(false); }
    public static Optional<RealmKey> profileRealm(CultivationProfile profile) {
        if (profile == null) return Optional.empty();
        try { return Optional.of(StatsRules.bundled().playerKey(profile.realmId().toString(), profile.stageId().toString())); }
        catch (IllegalArgumentException invalid) { return Optional.empty(); }
    }

    public Reading read(RealmKey observer, RealmKey target) {
        if (!unlocked(observer)) throw new IllegalArgumentException("Observation is not unlocked");
        int gap = Math.abs(rank(observer) - rank(target));
        if (gap <= exactDistance) return new Reading(true, target, target);
        var realm = stats.definition(target);
        int first = target.stage() / coarseStages * coarseStages;
        int last = Math.min(realm.stages() - 1, first + coarseStages - 1);
        return new Reading(false, stats.realmKey(realm.id(), first), stats.realmKey(realm.id(), last));
    }
    private int rank(RealmKey key) {
        stats.definition(key);
        int index = ladder.indexOf(key);
        if (index < 0) throw new IllegalArgumentException("Unknown observation realm");
        return index;
    }
    private static int integer(JsonObject object, String key, int minimum, int maximum) {
        var value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException(key);
        int result;
        try { result = value.getAsBigDecimal().intValueExact(); }
        catch (ArithmeticException invalid) { throw new IllegalArgumentException(key, invalid); }
        if (result < minimum || result > maximum) throw new IllegalArgumentException(key);
        return result;
    }
    private static String text(JsonObject object, String key) {
        var value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new IllegalArgumentException(key);
        return value.getAsString();
    }
}
