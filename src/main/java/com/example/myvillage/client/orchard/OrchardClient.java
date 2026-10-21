package com.example.myvillage.client.orchard;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.orchard.OrchardContent;
import net.minecraft.client.renderer.Sheets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@EventBusSubscriber(modid=MyVillageMod.MOD_ID,value=Dist.CLIENT)
public final class OrchardClient {
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(()->Sheets.addWoodType(OrchardContent.WOOD_TYPE));
    }
    private OrchardClient() { }
}
