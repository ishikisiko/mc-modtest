package com.example.myvillage.aptitude;

import com.mojang.serialization.Codec;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/** 独立的非负整数点数；100 只可能是某个培养资源的适用门槛。 */
public record AptitudeValues(Map<String, Integer> values) {
    public static final AptitudeValues EMPTY = new AptitudeValues(Map.of());
    public static final Codec<AptitudeValues> CODEC = Codec.unboundedMap(Codec.STRING, Codec.intRange(0, Integer.MAX_VALUE))
            .xmap(AptitudeValues::new, AptitudeValues::values);
    public AptitudeValues {
        if (values.size() > AptitudeRules.get().ids().size()) throw new IllegalArgumentException("Too many aptitudes");
        var copy = new TreeMap<String, Integer>();
        values.forEach((key, value) -> {
            if (!AptitudeRules.get().valid(key) || value == null || value < 0) throw new IllegalArgumentException("Invalid aptitude " + key);
            copy.put(key, value);
        });
        values = Collections.unmodifiableMap(copy);
    }
    public com.google.gson.JsonElement toJson() { return CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, this).getOrThrow(); }
    public static AptitudeValues fromJson(com.google.gson.JsonElement value) { return CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, value).getOrThrow(); }
    public int get(String key) { return values.getOrDefault(key, 0); }
    public AptitudeValues with(String key, int value) {
        var next = new TreeMap<>(values); next.put(key, value); return new AptitudeValues(next);
    }
    public static AptitudeValues generate(long seed, UUID identity, int realm, boolean all) {
        var rules = AptitudeRules.get(); var values = new TreeMap<String, Integer>();
        for (String id : all ? rules.ids() : rules.weapons()) {
            long hash = mix(seed ^ identity.getMostSignificantBits() ^ Long.rotateLeft(identity.getLeastSignificantBits(), 19) ^ id.hashCode());
            int value = rules.weaponMin() + (int) Math.floorMod(hash, (long) rules.weaponMax() - rules.weaponMin() + 1);
            values.put(id, AptitudeRules.add(value, Math.max(0, realm) * rules.npcPointsPerRealm()));
        }
        return new AptitudeValues(values);
    }
    private static long mix(long v) { v = (v ^ v >>> 30) * 0xbf58476d1ce4e5b9L; v = (v ^ v >>> 27) * 0x94d049bb133111ebL; return v ^ v >>> 31; }
}
