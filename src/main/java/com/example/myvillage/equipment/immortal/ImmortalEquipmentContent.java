package com.example.myvillage.equipment.immortal;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** 十五套独立材质、六十件护甲与十五种锭；不替换旧玄铁或武器注册。 */
public final class ImmortalEquipmentContent {
    private static final DeferredRegister<ArmorMaterial> MATERIALS =
            DeferredRegister.create(Registries.ARMOR_MATERIAL, "myvillage");
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("myvillage");
    private static final Map<ResourceLocation, DeferredItem<ImmortalArmorItem>> ARMOR = new LinkedHashMap<>();
    private static final Map<ResourceLocation, DeferredItem<Item>> INGOTS = new LinkedHashMap<>();

    static {
        for (var set : ImmortalEquipmentCatalogue.sets()) {
            var ingot = ITEMS.registerSimpleItem(set.ingot().id().getPath());
            INGOTS.put(set.ingot().id(), ingot);
            var tier = ImmortalEquipmentCatalogue.tiers().get(set.level());
            var materialId = ResourceLocation.fromNamespaceAndPath("myvillage", set.id());
            var material = MATERIALS.register(set.id(), () -> new ArmorMaterial(
                    tier.defense(), tier.enchantability(), SoundEvents.ARMOR_EQUIP_IRON,
                    // 本次仅接入外观，沿用铁甲基础修复材料，不新增灵材玩法。
                    () -> Ingredient.of(net.minecraft.world.item.Items.IRON_INGOT),
                    List.of(new ArmorMaterial.Layer(materialId)), tier.toughness(), tier.knockbackResistance()));
            for (var definition : set.armor()) {
                // DeferredHolder 本身是 Holder，不提前 material.get()，避免注册表时序依赖。
                ARMOR.put(definition.id(), ITEMS.registerItem(definition.id().getPath(),
                        properties -> new ImmortalArmorItem(properties, definition, material)));
            }
        }
    }

    private ImmortalEquipmentContent() { }

    /** 在模组构造阶段调用一次；两个注册表必须接入同一模组事件总线。 */
    public static void register(IEventBus bus) {
        MATERIALS.register(bus);
        ITEMS.register(bus);
    }

    /** 在创造栏构建事件中调用，此时所有延迟物品均已完成注册。 */
    public static void creative(CreativeModeTab.Output output) {
        for (var set : ImmortalEquipmentCatalogue.sets()) {
            output.accept(INGOTS.get(set.ingot().id()).get());
            for (var definition : set.armor()) output.accept(ARMOR.get(definition.id()).get());
        }
    }

    /** 客户端注册/验收入口，仅在物品注册结束后调用。 */
    public static ImmortalArmorItem[] armorItems() {
        return ARMOR.values().stream().map(DeferredItem::get).toArray(ImmortalArmorItem[]::new);
    }
}
