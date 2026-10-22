package com.example.myvillage.combat.runtime;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Bundled combat tuning; neither network packets nor player saves supply these values. */
public record DisciplineRules(Pool sword, Pool body, double spearMin, double spearMax,
                              double spearDamage, int spearStun) {
    public record Pool(int maximum, int timeoutTicks, double bonus) {}
    private static class Holder { private static final DisciplineRules VALUE = load(); }
    public static DisciplineRules get() { return Holder.VALUE; }
    private static DisciplineRules load() {
        try (var in = DisciplineRules.class.getResourceAsStream("/data/myvillage/combat/disciplines.json")) {
            if (in == null) throw new IllegalStateException("Missing disciplines.json");
            return parse(JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject());
        } catch (java.io.IOException ex) { throw new IllegalStateException(ex); }
    }
    public static DisciplineRules parse(JsonObject json) {
        if (number(json,"schema",1,1) != 1) throw new IllegalArgumentException("Invalid schema");
        var spear = json.getAsJsonObject("spear");
        double min = number(spear,"minimum_distance",0,8), max = number(spear,"maximum_distance",0,8);
        if (min >= max) throw new IllegalArgumentException("Invalid spear distance band");
        return new DisciplineRules(pool(json.getAsJsonObject("sword")), pool(json.getAsJsonObject("body")),
                min, max, number(spear,"damage_multiplier",1,2), integer(spear,"extra_stun_ticks",0,20));
    }
    private static Pool pool(JsonObject json) {
        return new Pool(integer(json,"maximum",1,10), integer(json,"timeout_ticks",20,1200),
                number(json,"bonus_per_stack",0,1));
    }
    private static int integer(JsonObject json, String key, int min, int max) {
        double value = number(json,key,min,max);
        if (value != Math.rint(value)) throw new IllegalArgumentException("Noninteger " + key);
        return (int)value;
    }
    private static double number(JsonObject json, String key, double min, double max) {
        if (json.get(key) == null || !json.get(key).isJsonPrimitive()
                || !json.getAsJsonPrimitive(key).isNumber()) throw new IllegalArgumentException("Invalid " + key);
        double value = json.get(key).getAsDouble();
        if (!Double.isFinite(value) || value < min || value > max) throw new IllegalArgumentException("Invalid " + key);
        return value;
    }
    public boolean spearTip(double distance) {
        return Double.isFinite(distance) && distance >= spearMin && distance <= spearMax;
    }
}
