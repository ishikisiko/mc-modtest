package com.example.myvillage.block;

import com.example.myvillage.MyVillageMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public final class ModBlocks {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModBlocks.class);

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MyVillageMod.MOD_ID);

    public static final DeferredBlock<PlaqueBlock> WALL_PLAQUE =
            BLOCKS.registerBlock("wall_plaque", PlaqueBlock::new, plaqueProperties());
    public static final DeferredBlock<PlaqueBlock> WALL_PLAQUE_VERTICAL =
            BLOCKS.registerBlock("wall_plaque_vertical", PlaqueBlock::new, plaqueProperties());
    public static final DeferredBlock<PlaqueBlock> HANGING_PLAQUE =
            BLOCKS.registerBlock("hanging_plaque", PlaqueBlock::new, plaqueProperties());
    public static final DeferredBlock<PlaqueBlock> HANGING_PLAQUE_VERTICAL =
            BLOCKS.registerBlock("hanging_plaque_vertical", PlaqueBlock::new, plaqueProperties());

    // 假山 (rockery) — first instance of the mod-decor-block-family protocol
    // (mod-owned sub-block-precision decorative block, no Chisels & Bits dep).
    public static final DeferredBlock<RockeryBlock> ROCKERY_BLOCK =
            BLOCKS.registerBlock("rockery_block", RockeryBlock::new, rockeryProperties());

    // 细瀑 (rockery cascade) — translucent water-textured trickle for the hero
    // 假山's visible 泉水细瀑 (add-hero-rockery task 2.6). Passable, visual-only.
    public static final DeferredBlock<RockeryCascadeBlock> ROCKERY_CASCADE =
            BLOCKS.registerBlock("rockery_cascade", RockeryCascadeBlock::new, cascadeProperties());

    private static final List<String> BLOCK_IDS = List.of(
            "wall_plaque",
            "wall_plaque_vertical",
            "hanging_plaque",
            "hanging_plaque_vertical",
            "rockery_block",
            "rockery_cascade");

    private ModBlocks() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }

    public static void verifyRegistered() {
        for (String id : BLOCK_IDS) {
            ResourceLocation key = ResourceLocation.fromNamespaceAndPath(MyVillageMod.MOD_ID, id);
            if (!BuiltInRegistries.BLOCK.containsKey(key)) {
                throw new IllegalStateException("Missing registered MyVillage block: " + key);
            }
        }
        LOGGER.info("Verified {} MyVillage blocks in BuiltInRegistries.BLOCK", BLOCK_IDS.size());
    }

    private static BlockBehaviour.Properties plaqueProperties() {
        return BlockBehaviour.Properties.of()
                .strength(0.8F)
                .noOcclusion();
    }

    private static BlockBehaviour.Properties rockeryProperties() {
        // Stone-class: slow to mine, occludes (the sub-block model is non-cube so
        // noOcclusion is not needed — the VoxelShape table governs light/cull).
        return BlockBehaviour.Properties.of()
                .strength(1.5F)
                .noOcclusion();
    }

    private static BlockBehaviour.Properties cascadeProperties() {
        // Visual-only trickle: passable (no collision), non-occluding, trivially
        // breakable, no loot (it is a structure-placed decoration, not an item).
        return BlockBehaviour.Properties.of()
                .noCollission()
                .noOcclusion()
                .strength(0.1F)
                .noLootTable();
    }
}
