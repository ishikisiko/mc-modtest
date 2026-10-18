package com.example.myvillage.courtyard;

import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class CourtyardPieceTest {
    static CourtyardPieceBlock block;
    @BeforeAll @SuppressWarnings("deprecation") static void bootstrap() {
        if(LoadingModList.get()==null)LoadingModList.of(List.of(),List.of(),List.of(),List.of(),Map.of());
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
        var sample=CourtyardCatalog.all().stream().filter(p->!p.full() && p.boxes().size()>3).findFirst().orElseThrow();
        var registry=(MappedRegistry<Block>)BuiltInRegistries.BLOCK;
        synchronized(registry){registry.unfreeze();try{
            block=Registry.register(registry,ResourceLocation.parse("myvillage_test:courtyard"),new CourtyardPieceBlock(sample.id(),BlockBehaviour.Properties.of().noOcclusion()));
        }finally{registry.freeze();}}
    }
    static boolean inside(VoxelShape shape,double x,double y,double z){return shape.toAabbs().stream().anyMatch(b->b.contains(x,y,z));}

    @Test void collisionIsExactUnderAllRotationsAndReflections() {
        for(var piece:CourtyardCatalog.all()) {
            for(boolean mirrored:List.of(false,true))for(int turn=0;turn<4;turn++) {
                var shape=piece.shape(CourtyardCatalog.facing(turn),mirrored);
                for(int x=0;x<4;x++)for(int y=0;y<4;y++)for(int z=0;z<4;z++) {
                    double px=(x+.5)/4,py=(y+.5)/4,pz=(z+.5)/4;
                    boolean expected=inside(piece.shape(Direction.NORTH,false),px,py,pz);
                    if(mirrored)px=1-px;
                    for(int i=0;i<turn;i++){double next=1-pz;pz=px;px=next;}
                    assertEquals(expected,inside(shape,px,py,pz),piece.id());
                }
            }
        }
    }
    @Test void stateTransformsComposeWithoutLosingWaterlogging() {
        var original=block.defaultBlockState().setValue(CourtyardPieceBlock.WATERLOGGED,true);
        assertEquals(16,block.getStateDefinition().getPossibleStates().size());
        for(boolean m:List.of(false,true))for(Direction d:List.of(Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST)) {
            var state=original.setValue(CourtyardPieceBlock.MIRRORED,m).setValue(CourtyardPieceBlock.FACING,d);
            assertEquals(state,state.rotate(Rotation.CLOCKWISE_90).rotate(Rotation.CLOCKWISE_90).rotate(Rotation.CLOCKWISE_90).rotate(Rotation.CLOCKWISE_90));
            assertEquals(state,state.mirror(Mirror.FRONT_BACK).mirror(Mirror.FRONT_BACK));
            assertEquals(state,state.mirror(Mirror.LEFT_RIGHT).mirror(Mirror.LEFT_RIGHT));
            assertEquals(state.rotate(Rotation.CLOCKWISE_180),state.mirror(Mirror.FRONT_BACK).mirror(Mirror.LEFT_RIGHT));
            assertTrue(state.mirror(Mirror.LEFT_RIGHT).getValue(CourtyardPieceBlock.WATERLOGGED));
        }
    }
    @Test void actualMirrorShapesMatchWorldReflection() {
        for(Direction d:List.of(Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST))for(boolean mirrored:List.of(false,true)) {
            var state=block.defaultBlockState().setValue(CourtyardPieceBlock.FACING,d).setValue(CourtyardPieceBlock.MIRRORED,mirrored);
            var shape=block.getShape(state,EmptyBlockGetter.INSTANCE,BlockPos.ZERO,null);
            var flipX=block.getShape(state.mirror(Mirror.FRONT_BACK),EmptyBlockGetter.INSTANCE,BlockPos.ZERO,null);
            var flipZ=block.getShape(state.mirror(Mirror.LEFT_RIGHT),EmptyBlockGetter.INSTANCE,BlockPos.ZERO,null);
            for(int x=0;x<4;x++)for(int y=0;y<4;y++)for(int z=0;z<4;z++) {
                double px=(x+.5)/4,py=(y+.5)/4,pz=(z+.5)/4;
                assertEquals(inside(shape,px,py,pz),inside(flipX,1-px,py,pz));
                assertEquals(inside(shape,px,py,pz),inside(flipZ,px,py,1-pz));
            }
        }
    }
    @Test void savedStateRetainsMirrorFacingAndWater() {
        var state=block.defaultBlockState().setValue(CourtyardPieceBlock.FACING,Direction.WEST).setValue(CourtyardPieceBlock.MIRRORED,true).setValue(CourtyardPieceBlock.WATERLOGGED,true);
        assertEquals(state,NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(),NbtUtils.writeBlockState(state)));
        assertTrue(state.getFluidState().isSource());
        assertEquals(Fluids.WATER,state.getFluidState().getType());
        var old=new CompoundTag();old.putString("Name","myvillage_test:courtyard");
        assertEquals(block.defaultBlockState(),NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(),old));
    }
    @Test void rejectsUnboundedBoxesAndVerticalFacing() {
        assertThrows(IllegalArgumentException.class,()->new CourtyardCatalog.Box(0,0,0,17,16,16));
        assertThrows(IllegalArgumentException.class,()->CourtyardCatalog.turn(Direction.UP));
        assertThrows(IllegalArgumentException.class,()->CourtyardCatalog.get("missing"));
    }
}
