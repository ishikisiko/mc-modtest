package com.example.myvillage.client;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.block.ModBlocks;
import net.minecraft.client.renderer.BiomeColors;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

/**
 * Client-only setup for MyVillage decor blocks (add-hero-rockery task 2.6).
 *
 * <p>Registers model tint handlers for decorative water and the hero
 * {@link ModBlocks#ROCKERY_BLOCK}. Hero models use tint index 0 for their baked
 * micro-water and tint index 1 for miniature oak foliage.
 */
@EventBusSubscriber(modid = MyVillageMod.MOD_ID, value = Dist.CLIENT)
public final class MyVillageClient {
    /** Vanilla default still-water color (used when no level/pos is available). */
    private static final int DEFAULT_WATER = 0x3F76E4;

    private MyVillageClient() {
    }

    @SubscribeEvent
    static void registerBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register(
                (state, level, pos, tintIndex) ->
                        (level != null && pos != null)
                                ? BiomeColors.getAverageWaterColor(level, pos)
                                : DEFAULT_WATER,
                ModBlocks.ROCKERY_CASCADE.get());
        event.register(
                (state, level, pos, tintIndex) -> {
                    if (tintIndex == 1) {
                        return (level != null && pos != null)
                                ? BiomeColors.getAverageFoliageColor(level, pos)
                                : 0x48B518;
                    }
                    return (level != null && pos != null)
                            ? BiomeColors.getAverageWaterColor(level, pos)
                            : DEFAULT_WATER;
                },
                ModBlocks.ROCKERY_BLOCK.get());
    }
}
