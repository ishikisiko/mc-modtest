package com.example.myvillage.equipment.immortal;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ArmorItem;

/** 随 jar 固定的身份与服务器属性目录；资源包重载不能改变物品或数值。 */
public final class ImmortalEquipmentCatalogue {
    private static final String PATH = "/data/myvillage/immortal_equipment/catalogue.json";
    private static final int LEVELS = 5;
    private static final int SETS_PER_LEVEL = 3;
    private static final Map<String, ArmorItem.Type> SLOTS = Map.of(
            "head", ArmorItem.Type.HELMET, "chest", ArmorItem.Type.CHESTPLATE,
            "legs", ArmorItem.Type.LEGGINGS, "feet", ArmorItem.Type.BOOTS);

    public record TierDefinition(int level, Map<ArmorItem.Type, Integer> defense,
                                 int durabilityMultiplier, int enchantability,
                                 float toughness, float knockbackResistance) {
        public TierDefinition {
            defense = Map.copyOf(defense);
        }
    }

    public record ArmorDefinition(ResourceLocation id, String setId, int level, ArmorItem.Type type,
                                  ResourceLocation model, ResourceLocation texture, String name,
                                  ResourceLocation ingotId, TierDefinition tier) { }

    public record IngotDefinition(ResourceLocation id, String setId, int level, String name) { }

    public record SetDefinition(String id, String name, int level, ResourceLocation model,
                                ResourceLocation texture, IngotDefinition ingot,
                                List<ArmorDefinition> armor) {
        public SetDefinition {
            armor = List.copyOf(armor);
        }
    }

    /** parse 的独立结果，不触发随 jar 目录加载，供纯数据测试与注册代码读取。 */
    public record Parsed(Map<Integer, TierDefinition> tiers, List<SetDefinition> sets,
                         List<ArmorDefinition> armor, List<IngotDefinition> ingots) {
        public Parsed {
            tiers = Collections.unmodifiableMap(new LinkedHashMap<>(tiers));
            sets = List.copyOf(sets);
            armor = List.copyOf(armor);
            ingots = List.copyOf(ingots);
        }
    }

    private ImmortalEquipmentCatalogue() { }

    public static List<ArmorDefinition> armor() { return Bundled.VALUE.armor(); }
    public static List<IngotDefinition> ingots() { return Bundled.VALUE.ingots(); }
    public static List<SetDefinition> sets() { return Bundled.VALUE.sets(); }
    public static Map<Integer, TierDefinition> tiers() { return Bundled.VALUE.tiers(); }

    private static final class Bundled {
        private static final Parsed VALUE = load();

        private static Parsed load() {
            try (var stream = ImmortalEquipmentCatalogue.class.getResourceAsStream(PATH)) {
                if (stream == null) throw new IllegalStateException("缺少五境装备目录：" + PATH);
                return parse(new InputStreamReader(stream, StandardCharsets.UTF_8));
            } catch (IOException | IllegalArgumentException error) {
                throw new IllegalStateException("无法加载五境装备目录：" + PATH, error);
            }
        }
    }

    /** 完整解析五级、十五套、六十件防具和十五种锭；缺项、重名或非法数值直接失败。 */
    public static Parsed parse(Reader reader) {
        try {
            return parseRoot(object(JsonParser.parseReader(reader), "目录"));
        } catch (IllegalArgumentException error) {
            throw error;
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("五境装备目录格式错误", error);
        }
    }

    private static Parsed parseRoot(JsonObject root) {
        if (integer(root, "version", 1, 1) != 1) throw invalid("不支持的目录版本");
        var tiers = parseTiers(object(required(root, "tiers"), "tiers"));
        var sourceSets = array(required(root, "sets"), "sets");
        if (sourceSets.size() != LEVELS * SETS_PER_LEVEL) throw invalid("必须包含十五套装备");
        var sets = new ArrayList<SetDefinition>();
        var armor = new ArrayList<ArmorDefinition>();
        var ingots = new ArrayList<IngotDefinition>();
        var setIds = new HashSet<String>();
        var itemIds = new HashSet<ResourceLocation>();
        int[] levelCounts = new int[LEVELS + 1];

        for (var element : sourceSets) {
            var source = object(element, "套装");
            var setId = text(source, "id");
            if (!setId.matches("[a-z][a-z0-9_]*") || !setIds.add(setId))
                throw invalid("套装 ID 非法或重复：" + setId);
            var name = text(source, "name");
            int level = integer(source, "level", 1, LEVELS);
            levelCounts[level]++;
            var tier = tiers.get(level);
            var model = location(source, "model");
            var texture = location(source, "texture");
            if (!model.equals(id("immortal_equipment/" + setId + ".json")))
                throw invalid("套装模型身份不匹配：" + model);
            if (!texture.getPath().matches("textures/immortal_equipment/" + setId + "/[a-z][a-z0-9_]*\\.png"))
                throw invalid("套装贴图路径不匹配：" + texture);

            var sourceIngot = object(required(source, "ingot"), "ingot");
            var ingotId = itemId(sourceIngot, setId + "_ingot", itemIds);
            var ingot = new IngotDefinition(ingotId, setId, level, text(sourceIngot, "name"));
            var sourceArmor = array(required(source, "armor"), "armor");
            if (sourceArmor.size() != SLOTS.size()) throw invalid("套装必须包含四个护甲槽位：" + setId);
            var pieces = new ArrayList<ArmorDefinition>();
            var seenSlots = new HashSet<ArmorItem.Type>();
            for (var piece : sourceArmor) {
                var sourcePiece = object(piece, "防具");
                var slot = text(sourcePiece, "slot");
                var type = SLOTS.get(slot);
                if (type == null || !seenSlots.add(type)) throw invalid("护甲槽位未知或重复：" + setId + "/" + slot);
                var armorId = itemId(sourcePiece, setId + "_" + type.getName(), itemIds);
                pieces.add(new ArmorDefinition(armorId, setId, level, type, model, texture,
                        text(sourcePiece, "name"), ingotId, tier));
            }
            sets.add(new SetDefinition(setId, name, level, model, texture, ingot, pieces));
            armor.addAll(pieces);
            ingots.add(ingot);
        }
        for (int level = 1; level <= LEVELS; level++) {
            if (levelCounts[level] != SETS_PER_LEVEL) throw invalid("每级必须包含三套装备：" + level);
        }
        return new Parsed(tiers, sets, armor, ingots);
    }

    private static Map<Integer, TierDefinition> parseTiers(JsonObject source) {
        if (source.size() != LEVELS) throw invalid("必须包含五级属性");
        var tiers = new LinkedHashMap<Integer, TierDefinition>();
        for (int level = 1; level <= LEVELS; level++) {
            var tier = object(required(source, Integer.toString(level)), "级别 " + level);
            var sourceDefense = object(required(tier, "defense"), "defense");
            if (!sourceDefense.keySet().equals(SLOTS.keySet())) throw invalid("防御值必须对应四个护甲槽位");
            var defense = new EnumMap<ArmorItem.Type, Integer>(ArmorItem.Type.class);
            SLOTS.forEach((slot, type) -> defense.put(type, integer(sourceDefense, slot, 0, Integer.MAX_VALUE)));
            // 原版耐久为槽位基数乘倍数；只限制溢出，不在 Java 复制 balance 的调参数值。
            int maxMultiplier = Integer.MAX_VALUE / ArmorItem.Type.CHESTPLATE.getDurability(1);
            tiers.put(level, new TierDefinition(level, defense,
                    integer(tier, "durability_multiplier", 1, maxMultiplier),
                    integer(tier, "enchantability", 0, Integer.MAX_VALUE),
                    number(tier, "toughness", Float.MAX_VALUE),
                    number(tier, "knockback_resistance", 1.0F)));
        }
        return tiers;
    }

    private static ResourceLocation itemId(JsonObject source, String expected, Set<ResourceLocation> seen) {
        var result = location(source, "id");
        if (!result.equals(id(expected)) || !seen.add(result)) throw invalid("物品 ID 不匹配或重复：" + result);
        return result;
    }

    private static ResourceLocation location(JsonObject source, String key) {
        var result = ResourceLocation.parse(text(source, key));
        if (!result.getNamespace().equals("myvillage")) throw invalid("资源必须使用 myvillage 命名空间：" + result);
        return result;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("myvillage", path);
    }

    private static JsonElement required(JsonObject source, String key) {
        var value = source.get(key);
        if (value == null || value.isJsonNull()) throw invalid("缺少字段：" + key);
        return value;
    }

    private static JsonObject object(JsonElement source, String field) {
        if (!source.isJsonObject()) throw invalid("字段必须是对象：" + field);
        return source.getAsJsonObject();
    }

    private static JsonArray array(JsonElement source, String field) {
        if (!source.isJsonArray()) throw invalid("字段必须是数组：" + field);
        return source.getAsJsonArray();
    }

    private static String text(JsonObject source, String key) {
        var value = required(source, key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw invalid("字段必须是文字：" + key);
        var result = value.getAsString();
        if (result.isBlank() || !result.equals(result.strip())) throw invalid("字段为空或包含首尾空白：" + key);
        return result;
    }

    private static int integer(JsonObject source, String key, int min, int max) {
        var value = required(source, key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw invalid("字段必须是整数：" + key);
        final int result;
        try {
            result = value.getAsBigDecimal().intValueExact();
        } catch (ArithmeticException | NumberFormatException error) {
            throw invalid("字段必须是有限整数：" + key);
        }
        if (result < min || result > max) throw invalid("整数超出范围：" + key);
        return result;
    }

    private static float number(JsonObject source, String key, float max) {
        var value = required(source, key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw invalid("字段必须是数值：" + key);
        double result = value.getAsDouble();
        if (!Double.isFinite(result) || result < 0 || result > max) throw invalid("数值非有限或超出范围：" + key);
        return (float) result;
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException("五境装备目录：" + message);
    }
}
