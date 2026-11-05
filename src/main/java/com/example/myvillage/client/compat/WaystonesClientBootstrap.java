package com.example.myvillage.client.compat;

import com.example.myvillage.MyVillageMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@EventBusSubscriber(modid = MyVillageMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class WaystonesClientBootstrap {
    private WaystonesClientBootstrap() { }

    @SubscribeEvent
    static void setup(FMLClientSetupEvent event) {
        if (ModList.get().isLoaded("waystones")) event.enqueueWork(WaystonesClientCompatibility::register);
    }
}
