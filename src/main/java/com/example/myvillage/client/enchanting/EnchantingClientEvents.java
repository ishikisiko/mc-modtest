package com.example.myvillage.client.enchanting;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.enchanting.EnchantingContent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = MyVillageMod.MOD_ID, value = Dist.CLIENT)
public final class EnchantingClientEvents {
    private EnchantingClientEvents() {}

    @SubscribeEvent public static void screens(RegisterMenuScreensEvent event) {
        event.register(EnchantingContent.MENU.get(), EnchantingScreen::new);
    }

    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(EnchantingContent.DEVICE.get(), EnchantingRenderer::new);
    }
}
