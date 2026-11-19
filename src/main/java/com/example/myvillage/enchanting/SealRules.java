package com.example.myvillage.enchanting;

import com.example.myvillage.item.ModItems;
import com.google.gson.annotations.SerializedName;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

public record SealRules(int strength_cap, int copper_strength, int draw_slots, int max_draw_slots,
        int direction_weight, int pairing_bonus, int treasure_weight, int risky_weight,
        int ordinary_risk, int treasure_risk, int risky_risk, List<Price> prices,
        Map<String, List<String>> directions, Map<String, List<Integer>> levels) {
    public static final SealRules RULES = EnchantingRules.read("seal_rules", SealRules.class);
    private static final TagKey<Enchantment> TREASURE = TagKey.create(Registries.ENCHANTMENT,
            ResourceLocation.withDefaultNamespace("treasure"));

    public enum Grade {
        @SerializedName("low") LOW("下品"),
        @SerializedName("medium") MEDIUM("中品");

        private final String label;

        Grade(String label) { this.label = label; }

        String label() { return label; }

        ItemStack item() {
            return switch (this) {
                case LOW -> ModItems.LOW_GRADE_SPIRIT_STONE.toStack();
                case MEDIUM -> ModItems.MEDIUM_GRADE_SPIRIT_STONE.toStack();
            };
        }
    }

    public record Price(int through, int stones, Grade grade) {
        public Price {
            Objects.requireNonNull(grade, "price grade");
        }
    }
    record Selection(int slots, String direction, String method, int pairing, boolean treasure, boolean first) {}

    public int strength(int base, List<String> discs) {
        return Math.min(strength_cap, base + copper_strength * count(discs, "copper"));
    }

    Price price(int strength) {
        return prices.stream().filter(band -> strength <= band.through()).findFirst().orElse(prices.getLast());
    }

    Selection selection(List<String> discs, boolean first, boolean treasure) {
        String method = discs.get(first ? 7 : 9);
        return new Selection(Math.min(max_draw_slots, draw_slots + (first ? count(discs, "crystal") : 0)),
                discs.get(first ? 6 : 8), method, first ? count(discs, "pairing") : 0,
                treasure && !method.equals("plain"), first);
    }

    int risk(Selection selection) {
        if (!selection.treasure()) return ordinary_risk;
        return selection.method().equals("risky") ? risky_risk : treasure_risk;
    }

    static boolean treasure(Holder<Enchantment> rune) {
        return !rune.is(EnchantmentTags.CURSE) && rune.is(TREASURE);
    }

    static boolean drawable(Holder<Enchantment> rune, boolean treasure) {
        if (rune.is(EnchantmentTags.CURSE)) return false;
        return SealRules.treasure(rune) ? treasure : RuneStorage.drawable(rune);
    }

    int level(Holder<Enchantment> rune, int strength) {
        var thresholds = levels.get(EnchantingRules.key(rune));
        if (thresholds == null) return EnchantingRules.RULES.maxLevel(rune, strength);
        int level = 0;
        while (level < thresholds.size() && thresholds.get(level) <= strength) level++;
        return level;
    }

    int supportedMax(Holder<Enchantment> rune) {
        return Math.max(rune.value().getMaxLevel(), levels.getOrDefault(EnchantingRules.key(rune), List.of()).size());
    }

    int weight(Holder<Enchantment> rune, Selection selection, List<Holder<Enchantment>> selected) {
        int weight = rune.value().getWeight();
        if (directions.getOrDefault(selection.direction(), List.of()).contains(EnchantingRules.key(rune))) weight *= direction_weight;
        if (treasure(rune)) {
            if (selection.method().equals("treasure")) weight *= treasure_weight;
            else if (selection.method().equals("risky")) weight *= risky_weight;
        }
        if (selection.pairing() > 0 && !selected.isEmpty()
                && selected.stream().allMatch(other -> Enchantment.areCompatible(rune, other))) {
            weight *= 1 + selection.pairing() * pairing_bonus;
        }
        return weight;
    }

    static String signature(int strength, boolean treasure, List<String> discs) {
        return strength + "/" + (treasure ? "1" : "0") + "/" + String.join(",", discs);
    }

    private static int count(List<String> discs, String type) {
        return (int) discs.stream().filter(type::equals).count();
    }
}
