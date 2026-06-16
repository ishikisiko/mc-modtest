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

    private static final List<String> BLOCK_IDS = List.of(
            "wall_plaque",
            "wall_plaque_vertical",
            "hanging_plaque",
            "hanging_plaque_vertical");

    private ModBlocks() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }

    public static void verifyRegistered() {
        for (String id : BLOCK_IDS) {
            ResourceLocation key = ResourceLocation.fromNamespaceAndPath(MyVillageMod.MOD_ID, id);
            if (!BuiltInRegistries.BLOCK.containsKey(key)) {
                throw new IllegalStateException("Missing registered MyVillage plaque block: " + key);
            }
        }
        LOGGER.info("Verified {} MyVillage plaque blocks in BuiltInRegistries.BLOCK", BLOCK_IDS.size());
    }

    private static BlockBehaviour.Properties plaqueProperties() {
        return BlockBehaviour.Properties.of()
                .strength(0.8F)
                .noOcclusion();
    }
}
