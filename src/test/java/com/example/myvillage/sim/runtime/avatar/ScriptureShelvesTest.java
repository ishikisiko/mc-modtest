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
    void aRootIsFoundOnlyAtTheDeclaredSite() {
        Column column=new Column();column.put(69,Blocks.STONE.defaultBlockState());
        assertEquals(69,ScriptureShelves.floorAt(column,SITE));
        column.put(70,shelf.defaultBlockState());column.put(71,shelf.defaultBlockState().setValue(ScriptureShelfBlock.HALF,DoubleBlockHalf.UPPER));
        assertEquals(SITE,ScriptureShelves.existingRoot(column,SITE));
        assertEquals(ScriptureShelves.NONE,ScriptureShelves.floorAt(column,SITE));
    }
    @Test
    void anObstructionCannotSendTheShelfToTheRoof() {
        Column column=new Column();column.put(69,Blocks.STONE.defaultBlockState());
        column.put(70,Blocks.BOOKSHELF.defaultBlockState());column.put(79,Blocks.STONE.defaultBlockState());
        assertEquals(ScriptureShelves.NONE,ScriptureShelves.floorAt(column,SITE));
        assertNull(ScriptureShelves.existingRoot(column,SITE));
        assertTrue(column.getBlockState(SITE).is(Blocks.BOOKSHELF));
    }
    @Test
    void anOrphanUpperOrAnotherRootCannotBeAdopted() {
        Column column=new Column();column.put(70,shelf.defaultBlockState().setValue(ScriptureShelfBlock.HALF,DoubleBlockHalf.UPPER));
        column.put(76,shelf.defaultBlockState());
        assertNull(ScriptureShelves.existingRoot(column,SITE));
        assertEquals(ScriptureShelves.NONE,ScriptureShelves.floorAt(column,SITE));
    }
}
