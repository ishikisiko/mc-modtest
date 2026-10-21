package com.example.myvillage.orchard;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Bundled tuning, shared by the world generator, growth and asset generator. */
public record OrchardRules(int trunkMin, int trunkExtra, int branchLength, int crownRadius,
        double fruitChance, int saplingChance, int fertileSaplingChance, int minimumLight,
        int fruitChanceTicks, int newClusterChance, float bonemealChance,
        int harvestCount, String lowItem, String highItem, int lanternLight) {
    private static class Holder { private static final OrchardRules VALUE = load(); }
    public static OrchardRules get() { return Holder.VALUE; }
    private static OrchardRules load() {
        try (var stream = OrchardRules.class.getResourceAsStream("/data/myvillage/orchard/rules.json")) {
            if (stream == null) throw new IllegalStateException("Missing orchard rules");
            return parse(JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject());
        } catch (java.io.IOException e) { throw new IllegalStateException(e); }
    }
    public static OrchardRules parse(JsonObject root) {
        if (root.get("schema").getAsInt() != 1) throw new IllegalArgumentException("Unsupported orchard rules");
        var t = root.getAsJsonObject("tree"); var g = root.getAsJsonObject("growth"); var h = root.getAsJsonObject("harvest");
        return new OrchardRules(integer(t,"trunk_min",4,7), integer(t,"trunk_extra",0,2),
                integer(t,"branch_length",1,2), integer(t,"crown_radius",1,2), fraction(t,"fruit_chance"),
                integer(g,"sapling_tick_chance",1,1000), integer(g,"fertile_sapling_tick_chance",1,1000),
                integer(g,"minimum_light",0,15), integer(g,"fruit_tick_chance",1,1000),
                integer(g,"new_cluster_tick_chance",1,10000), (float)fraction(g,"bonemeal_success"),
                integer(h,"count",1,4), id(h,"low_item"), id(h,"high_item"), integer(root,"lantern_light",0,15));
    }
    private static int integer(JsonObject o, String key, int min, int max) {
        double value=o.get(key).getAsDouble();
        if (!Double.isFinite(value) || value != Math.rint(value) || value<min || value>max)
            throw new IllegalArgumentException("Invalid orchard " + key);
        return (int)value;
    }
    private static double fraction(JsonObject o, String key) {
        double value=o.get(key).getAsDouble();
        if (!Double.isFinite(value) || value<0 || value>1) throw new IllegalArgumentException("Invalid orchard " + key);
        return value;
    }
    private static String id(JsonObject o, String key) {
        String value=o.get(key).getAsString();
        if (!value.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) throw new IllegalArgumentException("Invalid orchard item");
        return value;
    }
}
