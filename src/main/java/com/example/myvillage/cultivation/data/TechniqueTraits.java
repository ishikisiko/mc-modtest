package com.example.myvillage.cultivation.data;

import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.StreamSupport;

/** The workbench reads this same bundled definition for its trait links. */
public final class TechniqueTraits {
    private static final String RESOURCE = "/data/yuanxian/combat/technique_traits.json";
    private static final Set<String> ROLES = Set.of("获得", "施加", "读取", "消耗", "延续", "转化", "转移", "常驻");

    public record Trait(String id, String label, String role, String description) { }

    public enum ContactState {
        SWORD_INTENT, SPEAR_FORCE, SPEAR_STANCE, FIST_FORCE,
        PURSUIT, FIST_UNBALANCE, EARTH_UNBALANCE, ARMOR_BREAK, EROSION, CHILL, SCORCH, WARD, SHIELD,
        METAL_FORCE, WOOD_FORCE, WATER_FORCE, FIRE_FORCE, EARTH_FORCE;

        public String tag() {
            return this == FIST_UNBALANCE || this == EARTH_UNBALANCE
                    ? "unbalance" : name().toLowerCase(Locale.ROOT);
        }

        public boolean casterState() {
            return switch (this) {
                case PURSUIT, FIST_UNBALANCE, EARTH_UNBALANCE, ARMOR_BREAK, EROSION, CHILL, SCORCH -> false;
                default -> true;
            };
        }
    }

    public enum ContactRole { GAIN, SPEND, READ }

    public record ContactRule(ContactState state, ContactRole role) { }

    private record Catalogue(Map<ResourceLocation, List<Trait>> techniques, Map<String, Double> settings,
                             Map<ResourceLocation, String> heritageTags,
                             Map<ResourceLocation, List<ContactRule>> contactRules, HeartLinkRules heartLinks) { }

    private static final class Holder {
        static final Catalogue CATALOGUE = load();
    }

    private TechniqueTraits() { }

    public static List<Trait> of(ResourceLocation id) {
        return Holder.CATALOGUE.techniques().getOrDefault(id, List.of());
    }

    public static String heritageTag(ResourceLocation heritage) {
        return Holder.CATALOGUE.heritageTags().getOrDefault(heritage, "");
    }

    public static List<ContactRule> contactRules(ResourceLocation technique) {
        return Holder.CATALOGUE.contactRules().getOrDefault(technique, List.of());
    }

    public static HeartLinkRules heartLinks() {
        return Holder.CATALOGUE.heartLinks();
    }

    public static double number(String key) {
        Double value = Holder.CATALOGUE.settings().get(key);
        if (value == null) throw new IllegalArgumentException("未知功法特性参数 " + key);
        return value;
    }

    public static int integer(String key) {
        double value = number(key);
        if (value != Math.rint(value) || value > Integer.MAX_VALUE)
            throw new IllegalArgumentException("功法特性参数需要整数 " + key);
        return (int) value;
    }

    private static Catalogue load() {
        try (var input = TechniqueTraits.class.getResourceAsStream(RESOURCE)) {
            if (input == null) throw new IllegalStateException("缺少功法特性定义 " + RESOURCE);
            var root = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
            if (root.get("schema").getAsInt() != 1) throw new IllegalArgumentException("功法特性格式不符");
            var labels = root.getAsJsonObject("labels");
            Map<ResourceLocation, List<Trait>> techniques = new HashMap<>();
            for (var entry : root.getAsJsonObject("traits").entrySet()) {
                List<Trait> rows = StreamSupport.stream(entry.getValue().getAsJsonArray().spliterator(), false).map(row -> {
                    var values = row.getAsJsonArray();
                    String tag = values.get(0).getAsString();
                    String role = values.get(1).getAsString();
                    String description = values.get(2).getAsString();
                    if (values.size() != 3 || !labels.has(tag) || !ROLES.contains(role) || description.isBlank())
                        throw new IllegalArgumentException("功法特性无效 " + entry.getKey());
                    return new Trait(tag, labels.get(tag).getAsString(), role, description);
                }).toList();
                techniques.put(id(entry.getKey()), rows);
            }
            Map<String, Double> numbers = new HashMap<>();
            for (var entry : root.getAsJsonObject("settings").entrySet()) {
                double value = entry.getValue().getAsDouble();
                if (!Double.isFinite(value) || value <= 0)
                    throw new IllegalArgumentException("功法特性参数无效 " + entry.getKey());
                numbers.put(entry.getKey(), value);
            }
            Map<ResourceLocation, String> heritageTags = new HashMap<>();
            for (var entry : root.getAsJsonObject("heritage_tags").entrySet()) {
                var tag = labels.get(entry.getValue().getAsString());
                if (tag == null) throw new IllegalArgumentException("传承特性标签无效 " + entry.getKey());
                heritageTags.put(id(entry.getKey()), tag.getAsString());
            }
            Map<ResourceLocation, List<ContactRule>> rules = new HashMap<>();
            for (var entry : root.getAsJsonObject("contact_rules").entrySet()) {
                ResourceLocation technique = id(entry.getKey());
                var rows = StreamSupport.stream(entry.getValue().getAsJsonArray().spliterator(), false).map(row -> {
                    var values = row.getAsJsonArray();
                    var state = ContactState.valueOf(values.get(0).getAsString().toUpperCase(Locale.ROOT));
                    var role = ContactRole.valueOf(values.get(1).getAsString().toUpperCase(Locale.ROOT));
                    String label = switch (role) {
                        case GAIN -> state.casterState() ? "获得" : "施加";
                        case SPEND -> "消耗";
                        case READ -> "读取";
                    };
                    if (values.size() != 2 || (state == ContactState.ARMOR_BREAK) != (role == ContactRole.READ)
                            || state == ContactState.EROSION && role != ContactRole.GAIN
                            || state == ContactState.SCORCH || state == ContactState.WARD || state == ContactState.SHIELD
                            || state == ContactState.SPEAR_FORCE && role == ContactRole.GAIN
                            || techniques.getOrDefault(technique, List.of()).stream()
                                .noneMatch(trait -> trait.id().equals(state.tag()) && trait.role().equals(label)))
                        throw new IllegalArgumentException("命中特性规则无效 " + technique);
                    return new ContactRule(state, role);
                }).toList();
                if (rows.isEmpty() || rows.stream().map(ContactRule::state).distinct().count() != rows.size()
                        || rows.stream().filter(rule -> rule.role() != ContactRole.GAIN).count() > 1)
                    throw new IllegalArgumentException("命中特性规则重复 " + technique);
                rules.put(technique, rows);
            }
            var heartLinks = HeartLinkRules.parse(root.getAsJsonObject("heart_links"), labels);
            heartLinks.traits().forEach((id, rows) -> {
                if (techniques.putIfAbsent(id, rows) != null)
                    throw new IllegalArgumentException("心法特性重复 " + id);
            });
            return new Catalogue(Map.copyOf(techniques), Map.copyOf(numbers), Map.copyOf(heritageTags), Map.copyOf(rules), heartLinks);
        } catch (Exception e) {
            throw new IllegalStateException("无法加载功法特性定义 " + RESOURCE, e);
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("yuanxian", path);
    }
}
