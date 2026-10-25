package com.example.myvillage.entity.npc;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.function.IntUnaryOperator;

/** npcgen 生成的外观目录。公共代码可读取；不依赖客户端或 Minecraft 注册表。 */
public final class CultivatorVariants {
    public static final String RESOURCE = "/assets/myvillage/npc/cultivator_variants.json";
    private static final Catalog CATALOG = load();
    private static final List<Variant> NOVICES = CATALOG.variants().stream().filter(v -> v.family().equals("female_novice")).toList();
    private static final List<Variant> MALES = CATALOG.variants().stream().filter(v -> v.family().equals("male")).toList();

    public record Variant(String look, String face, String hair, String outfit, String family) { }
    record SpawnFamily(String family, int weight) { }
    record Catalog(List<String> legacy, List<Variant> variants, List<String> looks, String salt, String maleSalt,
                   List<SpawnFamily> spawnFamilies) { }

    private CultivatorVariants() { }

    public static List<String> looks() { return CATALOG.looks(); }
    public static List<String> legacyLooks() { return CATALOG.legacy(); }
    public static List<Variant> novices() { return NOVICES; }
    public static List<Variant> males() { return MALES; }

    /** 普通新生修士和刷怪蛋共用生成目录的族权重；旧 ID 只用于显式外观／存档兼容。 */
    public static String randomLook(IntUnaryOperator nextInt) {
        int choice = nextInt.applyAsInt(CATALOG.spawnFamilies().stream().mapToInt(SpawnFamily::weight).sum());
        for (SpawnFamily family : CATALOG.spawnFamilies()) {
            if (choice < family.weight()) {
                List<Variant> pool = family.family().equals("male") ? MALES : NOVICES;
                return pool.get(nextInt.applyAsInt(pool.size())).look();
            }
            choice -= family.weight();
        }
        throw new IllegalArgumentException("Random selection outside NPC family weights");
    }

    /** 只依赖命簿已有的永久身份。撤回重投影、受伤、改名或境界变化均不重抽组合。 */
    public static String forIdentity(int personId, long birthDay) {
        return select(novices(), CATALOG.salt(), personId, birthDay);
    }

    public static String forMaleIdentity(int personId, long birthDay) {
        return select(males(), CATALOG.maleSalt(), personId, birthDay);
    }

    private static String select(List<Variant> pool, String salt, int personId, long birthDay) {
        Variant selected = null;
        long best = 0;
        for (Variant variant : pool) {
            long score = hash(salt + ":" + personId + ":" + birthDay + ":" + variant.look());
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
        if (data.get("version").getAsInt() != 2) throw new IllegalArgumentException("Unknown NPC catalogue version");
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
                    item.get("hair").getAsString(), item.get("outfit").getAsString(), item.get("family").getAsString());
            String expected = switch (variant.family()) {
                case "female_novice" -> "f_novice_" + variant.face() + "_" + variant.hair().toLowerCase(java.util.Locale.ROOT) + "_" + variant.outfit();
                case "male" -> "m_" + variant.face() + "_" + variant.hair() + "_" + variant.outfit().toLowerCase(java.util.Locale.ROOT);
                default -> throw new IllegalArgumentException("Unknown NPC family: " + variant.family());
            };
            if (!variant.look().equals(expected) || !variant.look().matches("[a-z0-9_]+") || looks.contains(variant.look())) {
                throw new IllegalArgumentException("Invalid or duplicate NPC look: " + variant.look());
            }
            variants.add(variant);
            looks.add(variant.look());
        }
        if (variants.stream().noneMatch(v -> v.family().equals("male"))
                || variants.stream().noneMatch(v -> v.family().equals("female_novice"))
                || new HashSet<>(variants).size() != variants.size()) {
            throw new IllegalArgumentException("Empty or repeated NPC variants");
        }
        String salt = data.get("selection_salt").getAsString();
        String maleSalt = data.get("male_selection_salt").getAsString();
        if (salt.isBlank() || maleSalt.isBlank()) throw new IllegalArgumentException("Missing NPC identity salt");
        List<SpawnFamily> spawnFamilies = new ArrayList<>();
        var seenFamilies = new HashSet<String>();
        for (var item : data.getAsJsonArray("spawn_families")) {
            var row = item.getAsJsonObject();
            String family = row.get("family").getAsString();
            int weight = row.get("weight").getAsBigDecimal().intValueExact();
            if ((!family.equals("male") && !family.equals("female_novice")) || !seenFamilies.add(family)
                    || weight <= 0 || weight > 100) throw new IllegalArgumentException("Invalid NPC spawn family");
            spawnFamilies.add(new SpawnFamily(family, weight));
        }
        if (spawnFamilies.size() != 2) throw new IllegalArgumentException("Missing generated NPC spawn family");
        return new Catalog(List.copyOf(legacy), List.copyOf(variants), List.copyOf(looks), salt, maleSalt,
                List.copyOf(spawnFamilies));
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
