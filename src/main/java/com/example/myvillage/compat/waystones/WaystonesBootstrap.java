package com.example.myvillage.compat.waystones;

import com.example.myvillage.MyVillageMod;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

/** The optional API is resolved only after Waystones has been found. */
@EventBusSubscriber(modid = MyVillageMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class WaystonesBootstrap {
    private WaystonesBootstrap() { }

    @SubscribeEvent
    static void setup(FMLCommonSetupEvent event) {
        if (ModList.get().isLoaded("waystones")) {
            event.enqueueWork(WaystonesCompatibility::register);
        }
    }
}
