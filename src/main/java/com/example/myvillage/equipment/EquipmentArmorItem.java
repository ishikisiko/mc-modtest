package com.example.myvillage.equipment;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.ItemStack;

/** Iron-tier armor with authored visuals and shared forging durability. No extra damage reduction hook. */
public final class EquipmentArmorItem extends ArmorItem {
    private final EquipmentCatalogue.Entry definition;

    public EquipmentArmorItem(Properties properties, EquipmentCatalogue.Entry definition) {
        super(ArmorMaterials.IRON, definition.type(),
                properties.durability(definition.type().getDurability(definition.durabilityMultiplier())));
        this.definition = definition;
    }

    @Override public net.minecraft.network.chat.Component getName(ItemStack stack) {
        return net.minecraft.network.chat.Component.empty().append(super.getName(stack).copy().withStyle(s->s.withColor(com.example.myvillage.forging.ForgingQualityDisplay.color(
                com.example.myvillage.forging.ForgingQualityDisplay.quality(stack)))));
    }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context,
            java.util.List<net.minecraft.network.chat.Component> lines, net.minecraft.world.item.TooltipFlag flag) {
        super.appendHoverText(stack,context,lines,flag);
        var mark=stack.get(com.example.myvillage.item.ModDataComponents.FORGE_MARK.get());
        if(!lines.isEmpty())lines.set(0,lines.getFirst().copy().withStyle(s->s.withColor(com.example.myvillage.forging.ForgingQualityDisplay.color(mark==null?0:mark.quality()))));
        lines.add(com.example.myvillage.forging.ForgingQualityDisplay.name(mark==null?0:mark.quality()));
        if(mark!=null)lines.add(net.minecraft.network.chat.Component.translatable("tooltip.myvillage.forging."+mark.trait(),
                com.example.myvillage.forging.WorkshopEquipment.amount(com.example.myvillage.forging.ForgingRules.get().trait(mark.trait()).value(mark.quality()))));
        com.example.myvillage.forging.WorkshopEquipment.tooltip(stack,lines);
    }

    public EquipmentCatalogue.Entry definition() { return definition; }

    @Override
    public ResourceLocation getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot,
                                             ArmorMaterial.Layer layer, boolean innerModel) {
        return definition.texture();
    }
}
