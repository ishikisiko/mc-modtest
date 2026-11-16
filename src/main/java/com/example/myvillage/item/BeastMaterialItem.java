package com.example.myvillage.item;

import com.example.myvillage.drops.BeastMaterialRules;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;

/** Each realm/rarity combination has a stable item identity, so stack matching preserves value. */
public final class BeastMaterialItem extends Item {
    private final BeastMaterialRules.Variant variant;
    public BeastMaterialItem(Properties properties,BeastMaterialRules.Variant variant){super(properties.stacksTo(64));this.variant=variant;}
    public BeastMaterialRules.Variant variant(){return variant;}
    @Override public Component getName(ItemStack stack){return super.getName(stack).copy().withColor(variant.color());}
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> lines,TooltipFlag flag) {
        super.appendHoverText(stack,context,lines,flag);
        lines.add(Component.translatable("tooltip.yuanxian.beast_material.recycle",variant.price()).withStyle(ChatFormatting.GOLD));
        lines.add(Component.translatable("tooltip.yuanxian.beast_material.use").withStyle(ChatFormatting.GRAY));
    }
}
