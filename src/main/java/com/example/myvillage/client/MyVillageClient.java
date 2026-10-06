package com.example.myvillage.client;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.block.ModBlocks;
import com.example.myvillage.client.entity.RideableFlyingSwordRenderer;
import com.example.myvillage.client.entity.SimpleFoxRenderer;
import com.example.myvillage.client.entity.beast.BeastRenderer;
import com.example.myvillage.client.entity.npc.NpcRenderer;
import com.example.myvillage.entity.ModEntities;
import com.example.myvillage.entity.npc.CultivatorEntity;
import com.example.myvillage.item.ModItems;
import com.example.myvillage.item.TechniqueManualItem;
import net.minecraft.client.renderer.BiomeColors;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.registries.DeferredItem;

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

    /** Technique-manual grade colours (黄 玄 地 天), multiplied into the manual models' tint layer. */
    private static final int[] MANUAL_GRADE_COLORS = {0xC9A227, 0x3F6FB5, 0x8B5A2B, 0xE8D9A0};

    private MyVillageClient() {
    }

    @SubscribeEvent
    static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.SIMPLE_FOX.get(), SimpleFoxRenderer::new);
        event.registerEntityRenderer(
                ModEntities.RIDEABLE_FLYING_SWORD.get(),
                RideableFlyingSwordRenderer::new);
        BeastRenderer.register(event, ModEntities.DEMON_WOLF);
        NpcRenderer.register(event, ModEntities.CULTIVATOR, CultivatorEntity.LOOKS);
    }

    @SubscribeEvent
    static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        BeastRenderer.registerLayer(event, ModEntities.DEMON_WOLF.getId());
        NpcRenderer.registerLayer(event, ModEntities.CULTIVATOR.getId(), CultivatorEntity.LOOKS);
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

    /** Manual models: layer0 (line art) untinted, layer1 (tint mask, tint index 1) in the grade colour. */
    @SubscribeEvent
    static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        for (DeferredItem<TechniqueManualItem> manual : ModItems.MANUALS) {
            int gradeColor = 0xFF000000 | MANUAL_GRADE_COLORS[manual.get().grade() - 1];
            event.register((stack, tintIndex) -> tintIndex == 1 ? gradeColor : 0xFFFFFFFF, manual.get());
        }
    }
}
