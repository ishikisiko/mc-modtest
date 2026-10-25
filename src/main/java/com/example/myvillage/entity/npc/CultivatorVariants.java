package com.example.myvillage.entity.npc;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** npcgen 生成的外观目录。公共代码可读取；不依赖客户端或 Minecraft 注册表。 */
public final class CultivatorVariants {
    public static final String RESOURCE = "/assets/myvillage/npc/cultivator_variants.json";
    private static final Catalog CATALOG = load();

    public record Variant(String look, String face, String hair, String outfit) { }
    record Catalog(List<String> legacy, List<Variant> variants, List<String> looks, String salt) { }

    private CultivatorVariants() { }

    public static List<String> looks() { return CATALOG.looks(); }
    public static List<String> legacyLooks() { return CATALOG.legacy(); }
    public static List<Variant> novices() { return CATALOG.variants(); }

    /** 只依赖命簿已有的永久身份。撤回重投影、受伤、改名或境界变化均不重抽组合。 */
    public static String forIdentity(int personId, long birthDay) {
        Variant selected = null;
        long best = 0;
        for (Variant variant : novices()) {
            long score = hash(CATALOG.salt() + ":" + personId + ":" + birthDay + ":" + variant.look());
            if (selected == null || Long.compareUnsigned(score, best) > 0) {
                selected = variant;
                best = score;
            }
        }
        return selected.look();
    }

    // FNV-1a 64；Python catalog.selection_vectors 保留同源跨语言校验。
    private static long hash(String value) {
        long result = 0xcbf29ce484222325L;
        for (byte b : value.getBytes(StandardCharsets.UTF_8)) {
            result = (result ^ (b & 0xffL)) * 0x100000001b3L;
        }
        return result;
    }

    static Catalog parse(JsonObject data) {
        if (data.get("version").getAsInt() != 1) throw new IllegalArgumentException("Unknown NPC catalogue version");
        List<String> legacy = new ArrayList<>();
        data.getAsJsonArray("legacy_looks").forEach(value -> legacy.add(value.getAsString()));
        if (!legacy.equals(List.of("default", "f_novice", "f_adept"))) {
            throw new IllegalArgumentException("Legacy NPC look IDs must be preserved");
        }
        List<Variant> variants = new ArrayList<>();
        List<String> looks = new ArrayList<>(legacy);
        for (var value : data.getAsJsonArray("variants")) {
            var item = value.getAsJsonObject();
            Variant variant = new Variant(item.get("look").getAsString(), item.get("face").getAsString(),
                    item.get("hair").getAsString(), item.get("outfit").getAsString());
            if (!variant.look().matches("f_novice_[a-z0-9_]+") || looks.contains(variant.look())) {
                throw new IllegalArgumentException("Invalid or duplicate NPC look: " + variant.look());
            }
            variants.add(variant);
            looks.add(variant.look());
        }
        if (variants.isEmpty() || new HashSet<>(variants).size() != variants.size()) {
            throw new IllegalArgumentException("Empty or repeated NPC variants");
        }
        String salt = data.get("selection_salt").getAsString();
        if (salt.isBlank()) throw new IllegalArgumentException("Missing NPC identity salt");
        return new Catalog(List.copyOf(legacy), List.copyOf(variants), List.copyOf(looks), salt);
    }

    private static Catalog load() {
        try (var stream = CultivatorVariants.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) throw new IllegalStateException("Missing NPC catalogue " + RESOURCE);
            try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return parse(JsonParser.parseReader(reader).getAsJsonObject());
            }
        } catch (Exception failure) {
            throw new IllegalStateException("Invalid NPC catalogue " + RESOURCE, failure);
        }
    }
}
