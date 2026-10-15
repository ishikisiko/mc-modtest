package com.example.myvillage.block;

import java.lang.reflect.Proxy;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.*;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.neoforge.common.util.TriState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SpiritCropsTest {
    private static SpiritSoilBlock soil;
    private static SpiritCropBlock crop;
    private static ItemNameBlockItem herb;
    @BeforeAll @SuppressWarnings("deprecation") static void bootstrap() {
        if (LoadingModList.get() == null) LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        var blocks = (MappedRegistry<Block>) BuiltInRegistries.BLOCK;
        synchronized (blocks) {
            blocks.unfreeze();
            try {
                soil = Registry.register(blocks, ResourceLocation.parse("myvillage_test:spirit_soil"), new SpiritSoilBlock(BlockBehaviour.Properties.of()));
                crop = Registry.register(blocks, ResourceLocation.parse("myvillage_test:spirit_crop"), new SpiritCropBlock(BlockBehaviour.Properties.of().noCollission().randomTicks()));
            } finally { blocks.freeze(); }
        }
        var items = (MappedRegistry<Item>) BuiltInRegistries.ITEM;
        synchronized (items) {
            items.unfreeze();
            try {
                herb = Registry.register(items, ResourceLocation.parse("myvillage_test:spirit_herb"), new ItemNameBlockItem(crop, new Item.Properties()));
                herb.registerBlocks(Item.BY_BLOCK, herb);
            } finally { items.freeze(); }
        }
    }
    private static LevelReader world(BlockState support) {
        return (LevelReader) Proxy.newProxyInstance(LevelReader.class.getClassLoader(), new Class<?>[]{LevelReader.class},
                (proxy, method, args) -> switch(method.getName()) {
                    case "getBlockState" -> ((BlockPos) args[0]).equals(BlockPos.ZERO.below()) ? support : Blocks.AIR.defaultBlockState();
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
    @Test void exactlyThreeStagesAndBonemealAdvancesOneStage() {
        assertEquals(List.of(0,1,2), new ArrayList<>(SpiritCropBlock.AGE.getPossibleValues()));
        assertEquals(3, crop.getStateDefinition().getPossibleStates().size());
        assertEquals(2, crop.getMaxAge()); assertEquals(1, crop.getBonemealAgeIncrease(null));
        assertFalse(crop.isMaxAge(crop.getStateForAge(1))); assertTrue(crop.isMaxAge(crop.getStateForAge(2)));
        assertThrows(IllegalArgumentException.class, () -> crop.getStateForAge(3));
    }
    @Test void spiritPlantsRequireSpiritSoilAndVanillaPlantsAreRejected() {
        var state=crop.defaultBlockState();
        assertTrue(state.canSurvive(world(soil.defaultBlockState()),BlockPos.ZERO));
        assertFalse(state.canSurvive(world(Blocks.FARMLAND.defaultBlockState()),BlockPos.ZERO));
        assertFalse(state.canSurvive(world(Blocks.DIRT.defaultBlockState()),BlockPos.ZERO));
        assertEquals(TriState.TRUE, soil.canSustainPlant(soil.defaultBlockState(),null,BlockPos.ZERO,Direction.UP,state));
        assertEquals(TriState.FALSE, soil.canSustainPlant(soil.defaultBlockState(),null,BlockPos.ZERO,Direction.UP,Blocks.WHEAT.defaultBlockState()));
        assertEquals(TriState.FALSE, soil.canSustainPlant(soil.defaultBlockState(),null,BlockPos.ZERO,Direction.NORTH,state));
    }
    @Test void soilHasNoDryingStateOrRandomTickAndHerbIsThePlantingItem() {
        assertTrue(soil.isFertile(soil.defaultBlockState(),null,BlockPos.ZERO));
        assertTrue(soil.getStateDefinition().getProperties().isEmpty());
        assertFalse(soil.defaultBlockState().isRandomlyTicking());
        assertEquals(herb,crop.getBaseSeedId()); assertEquals(crop,herb.getBlock());
        assertEquals(herb,crop.getCloneItemStack(world(soil.defaultBlockState()),BlockPos.ZERO,crop.getStateForAge(2)).getItem());
    }
}
