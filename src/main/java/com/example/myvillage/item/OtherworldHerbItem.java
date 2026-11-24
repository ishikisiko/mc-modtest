package com.example.myvillage.item;

import com.example.myvillage.block.SpiritCropBlock;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemNameBlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public final class OtherworldHerbItem extends ItemNameBlockItem {
    public OtherworldHerbItem(SpiritCropBlock crop, Properties properties) {
        super(crop, properties);
    }

    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        var crop = (SpiritCropBlock) getBlock();
        crop.requiredSoil().ifPresent(soil -> lines.add(
                Component.translatable("tooltip.yuanxian.otherworld_herb.soil", soil.getName())));
    }
}
