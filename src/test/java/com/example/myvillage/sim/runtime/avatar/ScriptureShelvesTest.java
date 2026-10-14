package com.example.myvillage.sim.runtime.avatar;

import static org.junit.jupiter.api.Assertions.*;

import com.example.myvillage.block.ScriptureShelfBlock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ScriptureShelvesTest {
    private static final BlockPos SITE = new BlockPos(7, 70, -9);
    private static ScriptureShelfBlock shelf;

    @BeforeAll
    @SuppressWarnings("deprecation")
    static void bootstrap() {
        if (LoadingModList.get() == null) {
            LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        // Keep a real registered block fixture even when another suite bootstrapped first.
        MappedRegistry<Block> registry = (MappedRegistry<Block>) BuiltInRegistries.BLOCK;
        synchronized (registry) {
            registry.unfreeze();
            try {
                shelf = Registry.register(registry, ResourceLocation.fromNamespaceAndPath("myvillage_test", "scripture_shelf_column"),
                        new ScriptureShelfBlock(BlockBehaviour.Properties.of().noOcclusion()));
            } finally {
                registry.freeze();
            }
        }
    }

    private static final class Column implements BlockGetter {
        private final Map<BlockPos, BlockState> cells = new HashMap<>();

        void put(int y, BlockState state) { cells.put(new BlockPos(SITE.getX(), y, SITE.getZ()), state); }
        @Override public BlockState getBlockState(BlockPos pos) { return cells.getOrDefault(pos, Blocks.AIR.defaultBlockState()); }
        @Override public BlockEntity getBlockEntity(BlockPos pos) { return null; }
        @Override public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
        @Override public int getHeight() { return 384; }
        @Override public int getMinBuildHeight() { return -64; }
    }

    @Test
    void twoHalvesReplayOntoTheOriginalFloorInsteadOfStacking() {
        Column column = new Column();
        column.put(72, Blocks.STONE.defaultBlockState());
        column.put(73, shelf.defaultBlockState());
        column.put(74, shelf.defaultBlockState().setValue(ScriptureShelfBlock.HALF, DoubleBlockHalf.UPPER));
        assertEquals(72, ScriptureShelves.floorAt(column, SITE));
        assertEquals(new BlockPos(7, 73, -9), ScriptureShelves.existingRoot(column, SITE));
    }

    @Test
    void obstructedLegacyRootIsFoundBeforeScanCouldChooseTheRoof() {
        Column column = new Column();
        column.put(72, Blocks.STONE.defaultBlockState());
        column.put(73, shelf.defaultBlockState());
        column.put(74, Blocks.BOOKSHELF.defaultBlockState());
        column.put(79, Blocks.STONE.defaultBlockState());
        assertEquals(new BlockPos(7, 73, -9), ScriptureShelves.existingRoot(column, SITE));
        assertNotEquals(72, ScriptureShelves.floorAt(column, SITE), "obstruction rules out installing a new pair here");
        assertNull(ScriptureShelfBlock.upperRepairState(column.getBlockState(new BlockPos(7, 73, -9)),
                column.getBlockState(new BlockPos(7, 74, -9))));
        assertTrue(column.getBlockState(new BlockPos(7, 74, -9)).is(Blocks.BOOKSHELF));
    }

    @Test
    void surveySearchNeverTreatsAnOrphanUpperAsAnOwnedRoot() {
        Column column = new Column();
        column.put(73, shelf.defaultBlockState().setValue(ScriptureShelfBlock.HALF, DoubleBlockHalf.UPPER));
        assertNull(ScriptureShelves.existingRoot(column, SITE));
        column.put(76, shelf.defaultBlockState());
        assertEquals(new BlockPos(7, 76, -9), ScriptureShelves.existingRoot(column, SITE));
    }
}
