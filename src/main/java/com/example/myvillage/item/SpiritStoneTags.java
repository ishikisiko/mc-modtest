package com.example.myvillage.item;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Public datapack opt-in for equal-value low-grade stones; broader gem tags are classification only. */
public final class SpiritStoneTags {
    public static final String LOW_GRADE_REFERENCE = "#c:spirit_stones/low_grade";
    public static final TagKey<Item> LOW_GRADE_SPIRIT_STONE = TagKey.create(
            Registries.ITEM, ResourceLocation.parse(LOW_GRADE_REFERENCE.substring(1)));

    private SpiritStoneTags() { }

    public static boolean isLowGrade(ItemStack stack) {
        return !stack.isEmpty() && stack.is(LOW_GRADE_SPIRIT_STONE);
    }
}
