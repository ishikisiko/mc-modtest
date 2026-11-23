package com.example.myvillage.kitchen;

import com.example.myvillage.MyVillageMod;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** 固定随包食谱同时供注册、菜单、服务器加工和资源生成读取。 */
public final class KitchenRules {
    private static final String RESOURCE = "/data/yuanxian/kitchen/rules.json";

    public enum Station { MILL, BOARD, TABLE, STOVE }

    public record Ingredient(ResourceLocation item, int count) {
        public Ingredient {
            if (count < 1 || count > 64) throw new IllegalArgumentException("厨房投料数量无效");
        }

        public boolean matches(ItemStack stack) {
            return !stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(item);
        }

        public ItemStack stack() { return new ItemStack(BuiltInRegistries.ITEM.get(item), count); }
        public boolean bowl() { return item.equals(ResourceLocation.withDefaultNamespace("bowl")); }
    }

    public record Recipe(String id, Station station, String method, List<Ingredient> ingredients,
                         int count, int ticks) {
        public Recipe {
            ingredients = List.copyOf(ingredients);
            if (ingredients.isEmpty() || count < 1 || count > 64 || ticks < 0 || ticks > 1200
                    || station == Station.STOVE && ticks == 0
                    || station == Station.STOVE && ingredients.stream().filter(i -> !i.bowl()).count() > 5
                    || (station == Station.MILL || station == Station.BOARD) && ingredients.size() != 1
                    || ingredients.stream().map(Ingredient::item).distinct().count() != ingredients.size())
                throw new IllegalArgumentException("厨房配方无效：" + id);
        }

        public ItemStack result() { return new ItemStack(KitchenContent.item(id), count); }
        public boolean servedInBowl() { return ingredients.stream().anyMatch(Ingredient::bowl); }
    }

    public record Meal(Recipe recipe, int nutrition, int saturation, int percent) {
        public Meal {
            if (nutrition < 1 || nutrition > 20 || saturation < 0 || saturation > 20 || percent < 1 || percent > 100)
                throw new IllegalArgumentException("灵膳数值超出范围：" + recipe.id());
        }
        public String id() { return recipe.id(); }
    }

    public final int duration, batchLimit, knifeDurability, charcoalBatches, combatDelay, recoveryInterval;
    public final float useSeconds, recoveryFraction, referenceHealth;
    public final String ginger;
    public final List<Recipe> preparations;
    public final List<Meal> meals;
    public final List<Recipe> stove;

    private KitchenRules(JsonObject json) {
        duration = integer(json, "duration_ticks", 1, 72000);
        batchLimit = integer(json, "prep_batch_limit", 1, 64);
        knifeDurability = integer(json, "knife_durability", 1, 4096);
        charcoalBatches = integer(json, "charcoal_batches", 1, 64);
        combatDelay = integer(json, "combat_delay_ticks", 0, 1200);
        recoveryInterval = integer(json, "recovery_interval_ticks", 1, 1200);
        recoveryFraction = number(json, "recovery_fraction", 0.00001F, 0.1F);
        referenceHealth = number(json, "vanilla_reference_health", 1, 1024);
        useSeconds = number(json, "food_use_seconds", 0.1F, 10);
        ginger = json.getAsJsonObject("ginger").get("id").getAsString();

        preparations = objects(json.getAsJsonArray("prep")).stream().map(value ->
                recipe(value, Station.valueOf(value.get("station").getAsString().toUpperCase(Locale.ROOT)),
                        integer(value, "count", 1, 64))).toList();
        meals = objects(json.getAsJsonArray("meals")).stream().map(value ->
                new Meal(recipe(value, Station.STOVE, 1), integer(value, "nutrition", 1, 20),
                        integer(value, "saturation", 0, 20), integer(value, "percent", 1, 100))).toList();
        List<Recipe> cooking = new ArrayList<>();
        preparations.stream().filter(recipe -> recipe.station() == Station.STOVE).forEach(cooking::add);
        meals.stream().map(Meal::recipe).forEach(cooking::add);
        stove = List.copyOf(cooking);
        Set<String> ids = new HashSet<>();
        ids.add(ginger);
        preparations.forEach(recipe -> registerId(ids, recipe.id()));
        meals.forEach(meal -> registerId(ids, meal.id()));
        if (stove.isEmpty() || stove.size() > 64) throw new IllegalArgumentException("砖灶配方数量无效");
    }

    private static void registerId(Set<String> ids, String id) {
        if (!id.startsWith("kitchen_") || !ids.add(id)) throw new IllegalArgumentException("厨房物品 ID 无效：" + id);
    }

    public static KitchenRules get() { return Holder.VALUE; }

    private static final class Holder {
        static final KitchenRules VALUE = load();
    }

    private static KitchenRules load() {
        try (var stream = KitchenRules.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) throw new IllegalStateException("缺少厨房配置 " + RESOURCE);
            return new KitchenRules(JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject());
        } catch (IOException exception) {
            throw new IllegalStateException("无法读取厨房配置", exception);
        }
    }

    private static List<JsonObject> objects(JsonArray array) {
        List<JsonObject> result = new ArrayList<>();
        array.forEach(value -> result.add(value.getAsJsonObject()));
        return result;
    }

    private static Recipe recipe(JsonObject value, Station station, int count) {
        return new Recipe(value.get("id").getAsString(), station, value.get("method").getAsString(),
                objects(value.getAsJsonArray("ingredients")).stream().map(input ->
                        new Ingredient(ResourceLocation.parse(input.get("item").getAsString()),
                                integer(input, "count", 1, 64))).toList(),
                count, value.has("ticks") ? integer(value, "ticks", 1, 1200) : 0);
    }

    private static int integer(JsonObject json, String key, int min, int max) {
        double value = json.get(key).getAsDouble();
        if (value != Math.rint(value) || value < min || value > max) throw new IllegalArgumentException("厨房整数无效：" + key);
        return (int) value;
    }

    private static float number(JsonObject json, String key, float min, float max) {
        float value = json.get(key).getAsFloat();
        if (!Float.isFinite(value) || value < min || value > max) throw new IllegalArgumentException("厨房数值无效：" + key);
        return value;
    }

    public Recipe prep(Station station, ItemStack stack) {
        for (Recipe recipe : preparations) {
            if (recipe.station() == station && recipe.ingredients().getFirst().matches(stack)) return recipe;
        }
        return null;
    }

    static ResourceLocation id(String name) { return ResourceLocation.fromNamespaceAndPath(MyVillageMod.MOD_ID, name); }
}
