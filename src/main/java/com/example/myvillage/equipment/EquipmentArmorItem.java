package com.example.myvillage.equipment;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.ItemStack;

/** Ordinary iron-tier armor with authored visuals. No extra damage hook or cultivation effect. */
public final class EquipmentArmorItem extends ArmorItem {
    private final EquipmentCatalogue.Entry definition;

    public EquipmentArmorItem(Properties properties, EquipmentCatalogue.Entry definition) {
        super(ArmorMaterials.IRON, definition.type(),
                properties.durability(definition.type().getDurability(definition.durabilityMultiplier())));
        this.definition = definition;
    }

    public EquipmentCatalogue.Entry definition() { return definition; }

    @Override
    public ResourceLocation getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot,
                                             ArmorMaterial.Layer layer, boolean innerModel) {
        return definition.texture();
    }
}
