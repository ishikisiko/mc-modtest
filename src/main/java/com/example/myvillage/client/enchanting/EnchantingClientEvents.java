package com.example.myvillage.client.enchanting;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.enchanting.EnchantingContent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.ModelEvent;

@EventBusSubscriber(modid = MyVillageMod.MOD_ID, value = Dist.CLIENT)
public final class EnchantingClientEvents {
    private EnchantingClientEvents() {}

    @SubscribeEvent public static void screens(RegisterMenuScreensEvent event) {
        event.register(EnchantingContent.MENU.get(), EnchantingScreen::new);
    }

    @SubscribeEvent public static void models(ModelEvent.ModifyBakingResult event) {
        event.getModels().replaceAll((key, model) -> {
            if (!key.id().getNamespace().equals(MyVillageMod.MOD_ID) || key.variant().equals("inventory")) return model;
            return switch (key.id().getPath()) {
                case "library_outer", "library_equator", "library_oblique" ->
                        new LibrarySegmentModel(model, key.id().getPath().substring("library_".length()));
                default -> model;
            };
        });
    }

    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(EnchantingContent.DEVICE.get(), EnchantingRenderer::new);
    }
}
