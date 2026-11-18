package com.example.myvillage.enchanting;

import static com.example.myvillage.enchanting.EnchantingRules.key;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;

public final class RuneStorage {
    private RuneStorage() {}

    static final String CANDIDATES = "ArrayCandidates";
    static final String HELD = "ArrayHeld";
    static final String HELD_LEVEL = "ArrayHeldLevel";
    static final String SEAL = "ArraySeal";
    static final String NEGATIVE = "ArrayNegative";

    static CompoundTag read(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    static void update(ItemStack stack, java.util.function.Consumer<CompoundTag> action) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, action);
    }

    static void clearDraw(ItemStack stack) {
        update(stack, tag -> {
            tag.remove(CANDIDATES);
            tag.remove(HELD);
            tag.remove(HELD_LEVEL);
            tag.remove(SEAL);
            tag.remove(NEGATIVE);
        });
    }

    public static List<String> candidates(ItemStack stack) {
        return read(stack).getList(CANDIDATES, Tag.TAG_STRING).stream().map(Tag::getAsString).toList();
    }

    static void saveDraw(ItemStack stack, List<Holder<Enchantment>> batch, String held, int heldLevel) {
        saveDraw(stack, batch, held, heldLevel, "", "");
    }

    static void saveDraw(ItemStack stack, List<Holder<Enchantment>> batch, String held, int heldLevel,
            String seal, String negative) {
        var list = new ListTag();
        batch.forEach(rune -> list.add(StringTag.valueOf(key(rune))));
        update(stack, tag -> {
            tag.put(CANDIDATES, list);
            tag.putString(HELD, held);
            tag.putInt(HELD_LEVEL, heldLevel);
            tag.putString(SEAL, seal);
            tag.putString(NEGATIVE, negative);
        });
    }

    static int points(int level) {
        return level > 0 && level <= 20 ? 1 << (level - 1) : 0;
    }

    static boolean supported(Holder<Enchantment> rune, int level) {
        return rune.unwrapKey().isPresent() && !rune.is(EnchantmentTags.CURSE) && points(level) > 0 && level <= SealRules.RULES.supportedMax(rune);
    }

    static boolean drawable(Holder<Enchantment> rune) {
        return rune.is(EnchantmentTags.IN_ENCHANTING_TABLE) && !rune.is(EnchantmentTags.CURSE);
    }

    static Map<Holder<Enchantment>, Integer> effects(ItemStack stack) {
        Map<Holder<Enchantment>, Integer> result = new LinkedHashMap<>();
        EnchantmentHelper.getEnchantmentsForCrafting(stack).entrySet()
                .forEach(entry -> result.put(entry.getKey(), entry.getIntValue()));
        return result;
    }

    static boolean compatible(Holder<Enchantment> rune, Map<Holder<Enchantment>, Integer> existing) {
        return existing.keySet().stream().allMatch(other -> rune.equals(other) || Enchantment.areCompatible(rune, other));
    }

    static ItemStack write(ItemStack input, Map<Holder<Enchantment>, Integer> effects) {
        var result = input.is(Items.BOOK) ? input.transmuteCopy(Items.ENCHANTED_BOOK, 1) : input.copy();
        var value = new ItemEnchantments.Mutable(EnchantmentHelper.getEnchantmentsForCrafting(result));
        value.removeIf(rune -> !effects.containsKey(rune));
        effects.forEach(value::set);
        EnchantmentHelper.setEnchantments(result, value.toImmutable());
        clearDraw(result);
        return result;
    }
}
