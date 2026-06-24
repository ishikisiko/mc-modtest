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
 * <p>Registers a {@code BlockColor} for {@link ModBlocks#ROCKERY_CASCADE} so its
 * grayscale {@code water_still} texture (referenced with {@code tintindex: 0} in
 * the model) renders blue, matching the biome's water tint where a position is
 * available and falling back to vanilla's default water color otherwise. The
 * block's translucent render type is declared in the model JSON, so no Java
 * render-type registration is needed.
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
    }
}
