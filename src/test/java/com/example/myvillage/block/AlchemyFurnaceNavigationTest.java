package com.example.myvillage.block;

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
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AlchemyFurnaceNavigationTest {
    private static AlchemyFurnaceBlock furnace;

    @BeforeAll @SuppressWarnings("deprecation") static void bootstrap() {
        if (LoadingModList.get() == null) LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        var registry = (MappedRegistry<Block>) BuiltInRegistries.BLOCK;
        synchronized (registry) {
            registry.unfreeze();
            try {
                furnace = Registry.register(registry, ResourceLocation.parse("myvillage_test:npc_furnace"),
                        new AlchemyFurnaceBlock(BlockBehaviour.Properties.of()));
            } finally { registry.freeze(); }
        }
    }

    private static class Probe extends WalkNodeEvaluator {
        static PathType classify(BlockGetter view) { return getPathTypeFromState(view, BlockPos.ZERO); }
    }

    private record View(BlockState state) implements BlockGetter {
        @Override public BlockState getBlockState(BlockPos pos) {
            return pos.equals(BlockPos.ZERO) ? state : Blocks.AIR.defaultBlockState();
        }
        @Override public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
        @Override public BlockEntity getBlockEntity(BlockPos pos) { return null; }
        @Override public int getHeight() { return 16; }
        @Override public int getMinBuildHeight() { return -1; }
    }

    @Test void vanillaWalkingEvaluatorDoesNotTreatThePartialFurnaceAsAir() {
        assertEquals(PathType.OPEN, Probe.classify(new View(Blocks.AIR.defaultBlockState())));
        for (BlockState state : furnace.getStateDefinition().getPossibleStates()) {
            var view = new View(state);
            assertFalse(state.isCollisionShapeFullBlock(view, BlockPos.ZERO));
            assertEquals(PathType.BLOCKED, Probe.classify(view), state.toString());
        }
    }
}
