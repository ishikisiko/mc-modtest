package com.example.myvillage.worldplan.travel;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AtlasCityTravelSafetyTest {
    @BeforeAll static void bootstrap() {
        if (LoadingModList.get() == null) {
            LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }
    private static final AtlasCityLanding.Point ENTRY=new AtlasCityLanding.Point(0,64,0);
    private static final AABB BODY=new AABB(.2,64,.2,.8,65.8,.8);
    private static final class View implements BlockGetter {
        final Map<BlockPos,BlockState> blocks=new HashMap<>();
        View() { blocks.put(new BlockPos(0,63,0),Blocks.STONE.defaultBlockState()); }
        public BlockState getBlockState(BlockPos at) { return blocks.getOrDefault(at,Blocks.AIR.defaultBlockState()); }
        public FluidState getFluidState(BlockPos at) { return getBlockState(at).getFluidState(); }
        public BlockEntity getBlockEntity(BlockPos at) { return null; }
        public int getHeight() { return 384; }
        public int getMinBuildHeight() { return -64; }
    }
    private boolean safe(View view) {
        return new AtlasCityTravelSafety(AtlasCityTravelRules.bundled()).blocksSafe(view,view::getBlockState,ENTRY,BODY);
    }
    @Test void requiresActualFullFloorAndBothStandingAirBlocks() {
        View view=new View();assertTrue(safe(view));
        view.blocks.clear();assertFalse(safe(view));
        view=new View();view.blocks.put(new BlockPos(0,63,0),Blocks.STONE_SLAB.defaultBlockState());assertFalse(safe(view));
        view=new View();view.blocks.put(new BlockPos(0,64,0),Blocks.STONE.defaultBlockState());assertFalse(safe(view));
        view=new View();view.blocks.put(new BlockPos(0,65,0),Blocks.STONE.defaultBlockState());assertFalse(safe(view));
    }
    @Test void liquidsDamageBlocksAndPortalsAreRejectedWithoutChangingTheWorld() {
        for (var block : new net.minecraft.world.level.block.Block[]{Blocks.WATER,Blocks.LAVA,Blocks.FIRE,Blocks.MAGMA_BLOCK,
                Blocks.CACTUS,Blocks.SWEET_BERRY_BUSH,Blocks.WITHER_ROSE,Blocks.POWDER_SNOW,
                Blocks.CAMPFIRE,Blocks.POINTED_DRIPSTONE,Blocks.NETHER_PORTAL,Blocks.END_PORTAL,Blocks.END_GATEWAY}) {
            View view=new View();view.blocks.put(new BlockPos(0,64,0),block.defaultBlockState());
            var before=Map.copyOf(view.blocks);assertFalse(safe(view),block.toString());assertEquals(before,view.blocks);
        }
        View view=new View();view.blocks.put(new BlockPos(0,63,0),Blocks.MAGMA_BLOCK.defaultBlockState());assertFalse(safe(view));
        view=new View();view.blocks.put(new BlockPos(1,64,0),Blocks.CACTUS.defaultBlockState());assertFalse(safe(view));
    }
    @Test void missingActualBlockIsUnsafeRatherThanTreatedAsAir() {
        View view=new View();var safety=new AtlasCityTravelSafety(AtlasCityTravelRules.bundled());
        assertFalse(safety.blocksSafe(view,at -> at.equals(new BlockPos(0,65,0))?null:view.getBlockState(at),ENTRY,BODY));
    }
}
