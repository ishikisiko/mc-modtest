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

    /** 公共生成代码的可选入口；缺少 Waystones 时不解析其类型。 */
    public static void placeCapitalStones(com.example.myvillage.worldplan.capital.CapitalCity city,
                                          net.minecraft.world.level.WorldGenLevel level,
                                          net.minecraft.world.level.chunk.ChunkAccess chunk) {
        if (ModList.get().isLoaded("waystones")) CapitalWaystonesCompatibility.place(city, level, chunk);
    }
}
