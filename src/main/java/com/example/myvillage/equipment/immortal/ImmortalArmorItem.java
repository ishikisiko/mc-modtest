package com.example.myvillage.equipment.immortal;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;

/** 原版护甲负责穿戴、附魔、修复、耐久与属性；本类只提供目录身份和贴图入口。 */
public final class ImmortalArmorItem extends ArmorItem {
    private final ImmortalEquipmentCatalogue.ArmorDefinition definition;

    public ImmortalArmorItem(Properties properties, ImmortalEquipmentCatalogue.ArmorDefinition definition,
                             Holder<ArmorMaterial> material) {
        super(material, definition.type(),
                properties.durability(definition.type().getDurability(definition.tier().durabilityMultiplier())));
        this.definition = definition;
    }

    public ImmortalEquipmentCatalogue.ArmorDefinition definition() { return definition; }

    @Override
    public ResourceLocation getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot,
                                            ArmorMaterial.Layer layer, boolean innerModel) {
        return definition.texture();
    }
}
