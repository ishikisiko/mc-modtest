package com.example.myvillage.sim.runtime.life;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.Node;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NpcLifeNavigationTest {
    @BeforeAll static void bootstrap() {
        if (LoadingModList.get() == null) LoadingModList.of(List.of(),List.of(),List.of(),List.of(),Map.of());
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
    }
    private record View(Map<BlockPos,BlockState> cells) implements BlockGetter {
        View() { this(new HashMap<>()); }
        View put(int x,int y,int z,BlockState s) { cells.put(new BlockPos(x,y,z),s);return this; }
        @Override public BlockState getBlockState(BlockPos p) { return cells.getOrDefault(p,
                p.getY()<0 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState()); }
        @Override public FluidState getFluidState(BlockPos p) { return getBlockState(p).getFluidState(); }
        @Override public BlockEntity getBlockEntity(BlockPos p) { return null; }
        @Override public int getHeight() { return 32; }
        @Override public int getMinBuildHeight() { return -1; }
    }
    private boolean step(View view,Node a,Node b) { return NpcLifeNavigation.clearStep(view,a,b,.6,1.95,true); }
    @Test void flatCorridorAndClearStepAllowBothDirections() {
        View v=new View();assertTrue(step(v,new Node(0,0,0),new Node(1,0,0)));
        v.put(1,0,0,Blocks.STONE.defaultBlockState());
        assertTrue(step(v,new Node(0,0,0),new Node(1,1,0)));
        assertTrue(step(v,new Node(1,1,0),new Node(0,0,0)));
    }
    @Test void jumpCannotClipHeadThroughLowCeiling() {
        var v=new View().put(1,0,0,Blocks.STONE.defaultBlockState()).put(0,2,0,Blocks.STONE.defaultBlockState());
        assertFalse(step(v,new Node(0,0,0),new Node(1,1,0)));
    }
    @Test void dropCannotCrossOverhangEvenWithClearLanding() {
        var v=new View().put(0,1,0,Blocks.STONE.defaultBlockState()).put(1,2,0,Blocks.STONE.defaultBlockState());
        assertFalse(step(v,new Node(0,2,0),new Node(1,0,0)));
        v.cells().remove(new BlockPos(1,2,0));assertTrue(step(v,new Node(0,2,0),new Node(1,0,0)));
    }
    @Test void diagonalCannotCutThroughWallCorner() {
        var v=new View().put(1,0,0,Blocks.STONE.defaultBlockState());
        assertFalse(step(v,new Node(0,0,0),new Node(1,0,1)));
    }
    @Test void partialFurnitureCollisionIsNotEmptyAir() {
        var v=new View().put(1,0,0,Blocks.END_ROD.defaultBlockState());
        assertFalse(step(v,new Node(0,0,0),new Node(1,0,0)));
    }
    @Test void onlyHandOpenableDoorsCanBeTraversedWhileClosed() {
        // EAST puts the closed panel across this eastward crossing, not parallel to it.
        var v=new View().put(1,0,0,Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING,Direction.EAST));
        assertTrue(step(v,new Node(0,0,0),new Node(1,0,0)));
        v.put(1,0,0,Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.FACING,Direction.EAST));
        assertFalse(step(v,new Node(0,0,0),new Node(1,0,0)));
    }
}
