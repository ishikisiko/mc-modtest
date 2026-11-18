package com.example.myvillage.enchanting;

import com.example.myvillage.MyVillageMod;
import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.enchantment.Enchantment;

public record EnchantingRules(int empty_cap, int shelf_cap, int candidates, int maximum_candidates,
        int seal_candidates, int preference_weight, int lapis_cost, int xp_cost, int wash_ink, int swap_ink,
        int lock_strength, int double_strength, int point_capacity, Map<String, List<Integer>> level_thresholds,
        Map<String, Rune> overrides, List<Stone> stones) {
    private static final Gson GSON = new Gson();
    public static final EnchantingRules RULES = read("rules", EnchantingRules.class);

    public record Rune(List<Integer> thresholds, int factor) {}
    public record Stone(String id, int strength, String stage, long progress, String ingredient, int count) {}

    public int maxLevel(Holder<Enchantment> enchantment, int strength) {
        var rune = overrides.get(key(enchantment));
        int max = enchantment.value().getMaxLevel();
        var thresholds = rune == null ? level_thresholds.get(Integer.toString(max)) : rune.thresholds();
        int level = 0;
        for (int i = 0; i < max; i++) {
            int threshold = thresholds != null && i < thresholds.size() ? thresholds.get(i)
                    : max == 1 ? 0 : (int) Math.ceil(i * (double) shelf_cap / (max - 1));
            if (threshold <= strength) level = i + 1;
        }
        return level;
    }

    public int ink(Holder<Enchantment> enchantment, int from, int to) {
        var rune = overrides.get(key(enchantment));
        return (rune == null ? 1 : rune.factor()) * (to * (to + 1) - from * (from + 1)) / 2;
    }

    static String key(Holder<Enchantment> enchantment) {
        return enchantment.unwrapKey().orElseThrow().location().toString();
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MyVillageMod.MOD_ID, path);
    }

    public static <T> T read(String name, Class<T> type) {
        String path = "/data/" + MyVillageMod.MOD_ID + "/enchanting/" + name + ".json";
        try (var stream = Objects.requireNonNull(EnchantingRules.class.getResourceAsStream(path), path);
             var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return GSON.fromJson(reader, type);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(path, e);
        }
    }
}
