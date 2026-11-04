package com.example.myvillage.story.capital;

import com.example.myvillage.sim.life.CapitalResidentDefinition;
import com.example.myvillage.sim.life.CapitalResidents;
import com.google.gson.*;
import java.io.Reader;
import java.util.*;

/** The packaged definition owns residents, work quantities and the permitted planting range. */
public record CapitalOpeningRules(List<CapitalResidentDefinition> residents, String seedItem,
        String soilItem, int workCount, int workRadius, Map<String, Map<String, Places>> placeOverrides) {
    public record Places(String home, String work) {
        public Places { com.example.myvillage.sim.life.LifePlace.requireId(home); com.example.myvillage.sim.life.LifePlace.requireId(work); }
    }
    public static final String RESOURCE = "story/capital_opening.json";
    public CapitalOpeningRules {
        residents = List.copyOf(residents);
        var overrides = new TreeMap<String, Map<String, Places>>();
        placeOverrides.forEach((region, roles) -> {
            if (!region.matches("[a-z0-9_]{1,64}") || !CapitalResidents.ROLES.containsAll(roles.keySet()))
                throw new IllegalArgumentException("invalid_capital_place_override");
            overrides.put(region, Map.copyOf(roles));
        });
        placeOverrides = Map.copyOf(overrides);
        if (residents.size() != CapitalResidents.ROLES.size()
                || !new HashSet<>(residents.stream().map(CapitalResidentDefinition::role).toList()).equals(CapitalResidents.ROLES)
                || !seedItem.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || !soilItem.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")
                || workCount < 1 || workCount > CapitalOpeningProgress.MAX_PLANTS || workRadius < 16 || workRadius > 512)
            throw new IllegalArgumentException("invalid_capital_opening");
    }
    public List<CapitalResidentDefinition> residents(String region) {
        var roles = placeOverrides.getOrDefault(region, Map.of());
        return residents.stream().map(d -> {
            var p = roles.get(d.role());
            return p == null ? d : new CapitalResidentDefinition(d.role(), p.home(), p.work(), d.surname(), d.given(),
                    d.gender(), d.ageYears(), d.realm(), d.stage(), d.root(), d.traits());
        }).toList();
    }
    public static CapitalOpeningRules read(Reader reader) {
        JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
        if (integer(root, "schema") != 1) throw new IllegalArgumentException("capital_opening_schema");
        var people = new ArrayList<CapitalResidentDefinition>();
        for (var element : root.getAsJsonArray("residents")) {
            var p = element.getAsJsonObject();
            people.add(new CapitalResidentDefinition(text(p,"role"), text(p,"home"), text(p,"work"),
                    text(p,"surname"), text(p,"given"), text(p,"gender"), integer(p,"age_years"),
                    text(p,"realm"), integer(p,"stage"), numbers(p,"root"), numbers(p,"traits")));
        }
        var work = root.getAsJsonObject("work");
        var overrides = new TreeMap<String, Map<String, Places>>();
        for (var region : root.getAsJsonObject("place_overrides").entrySet()) {
            var roles = new TreeMap<String, Places>();
            for (var role : region.getValue().getAsJsonObject().entrySet()) {
                var p = role.getValue().getAsJsonObject(); roles.put(role.getKey(), new Places(text(p,"home"), text(p,"work")));
            }
            overrides.put(region.getKey(), roles);
        }
        return new CapitalOpeningRules(people, text(work,"seed_item"), text(work,"soil_item"),
                integer(work,"count"), integer(work,"outside_city_radius"), overrides);
    }
    private static List<Integer> numbers(JsonObject o, String key) {
        var out = new ArrayList<Integer>();
        for (var e : o.getAsJsonArray(key)) out.add(number(e));
        return out;
    }
    private static int integer(JsonObject o, String key) { return number(o.get(key)); }
    private static int number(JsonElement e) {
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException("expected_integer");
        return e.getAsBigDecimal().intValueExact();
    }
    private static String text(JsonObject o, String key) {
        var e = o.get(key);
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) throw new IllegalArgumentException("expected_text:" + key);
        return e.getAsString();
    }
}
