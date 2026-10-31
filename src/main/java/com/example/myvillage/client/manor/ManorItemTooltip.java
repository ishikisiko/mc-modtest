package com.example.myvillage.client.manor;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.manor.ManorPresentation;
import com.example.myvillage.manor.ManorComponentItem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

@EventBusSubscriber(modid=MyVillageMod.MOD_ID,value=Dist.CLIENT)
public final class ManorItemTooltip {
    @SubscribeEvent public static void tooltip(ItemTooltipEvent event) {
        if(!(event.getItemStack().getItem() instanceof ManorComponentItem))return;
        ManorPresentation.get().describe(event.getItemStack()).ifPresent(info -> {
            var lines=event.getToolTip();
            if(Screen.hasShiftDown()) {
                lines.add(1,info.materialsLine().copy().withStyle(ChatFormatting.GRAY));
                lines.add(2,info.styleLine().copy().withStyle(ChatFormatting.DARK_GRAY));
            } else lines.add(1,Component.translatable("tooltip.myvillage.manor_details").withStyle(ChatFormatting.DARK_GRAY));
        });
    }
    private ManorItemTooltip() { }
}
