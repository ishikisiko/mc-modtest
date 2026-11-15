package com.example.myvillage.drops;

import com.example.myvillage.alchemy.PillQuality;
import com.example.myvillage.equipment.EquipmentArmorItem;
import com.example.myvillage.forging.ForgeMark;
import com.example.myvillage.forging.WorkshopTool;
import com.example.myvillage.item.*;
import java.util.Set;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;

/** Applies the existing item-family components to newly generated drop stacks. */
public final class DropItemVariants {
    private static final Set<String> RARITIES = Set.of("common", "uncommon", "rare", "epic");

    private DropItemVariants() {}

    public static String qualityKind(Item item) {
        if (item instanceof AlchemyPillItem || item instanceof FoundationPillItem) return "pill";
        if (item instanceof EquipmentArmorItem || item instanceof CombatWeaponItem || item instanceof WorkshopTool)
            return "equipment";
        return "";
    }

    public static boolean allowsQuality(String id, int quality) {
        var item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
        return switch (qualityKind(item)) {
            case "pill" -> quality >= 1 && quality <= 4;
            case "equipment" -> quality >= 0 && quality <= 4;
            default -> false;
        };
    }

    public static boolean allowsRarity(String id) {
        var item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
        return qualityKind(item).isEmpty() && !(item instanceof NingyuanFruitItem);
    }

    public static boolean validRarity(String value) {
        return RARITIES.contains(value);
    }

    public static void apply(ItemStack stack, Integer quality, String rarity) {
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        if (quality != null && rarity != null) throw new IllegalArgumentException("品质与原版稀有度不能同时指定");
        if (quality != null) {
            if (!allowsQuality(id, quality)) throw new IllegalArgumentException("物品不支持指定品质：" + id);
            if (qualityKind(stack.getItem()).equals("pill")) PillQuality.apply(stack, quality);
            else if (quality == 0) stack.remove(ModDataComponents.FORGE_MARK.get());
            else stack.set(ModDataComponents.FORGE_MARK.get(), ForgeMark.uninfused(quality));
        }
        if (rarity != null) {
            if (!allowsRarity(id) || !validRarity(rarity)) throw new IllegalArgumentException("物品稀有度无效：" + id);
            stack.set(DataComponents.RARITY, switch (rarity) {
                case "common" -> Rarity.COMMON;
                case "uncommon" -> Rarity.UNCOMMON;
                case "rare" -> Rarity.RARE;
                case "epic" -> Rarity.EPIC;
                default -> throw new IllegalArgumentException("未知稀有度");
            });
        }
    }
}
