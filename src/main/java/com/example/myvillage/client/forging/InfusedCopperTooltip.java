package com.example.myvillage.client.forging;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.forging.InfusionRules;
import com.example.myvillage.item.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

@EventBusSubscriber(modid=MyVillageMod.MOD_ID,value=Dist.CLIENT)
public final class InfusedCopperTooltip {
    @SubscribeEvent public static void tooltip(ItemTooltipEvent event){
        var stack=event.getItemStack();var progress=stack.get(ModDataComponents.COPPER_INFUSION.get());
        if(progress!=null)
            event.getToolTip().add(Component.translatable("tooltip.myvillage.infused_copper",progress.paidQi(),InfusionRules.get().qiPerIngot()).withStyle(ChatFormatting.GOLD));
    }
}
