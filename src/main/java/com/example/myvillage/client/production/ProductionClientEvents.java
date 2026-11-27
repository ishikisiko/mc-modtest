package com.example.myvillage.client.production;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.production.ProductionContent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = MyVillageMod.MOD_ID, value = Dist.CLIENT)
public final class ProductionClientEvents {
    @SubscribeEvent public static void screens(RegisterMenuScreensEvent event) {
        event.register(ProductionContent.MENU.get(), ProductionScreen::new);
    }

    private ProductionClientEvents() { }
}
