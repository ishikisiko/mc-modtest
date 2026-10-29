package com.example.myvillage.streetfurniture;

import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.world.level.block.Block;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StreetFurnitureTest {
    private static RuneLampBlock lamp;
    private static DryingRackBlock rack;

    @BeforeAll @SuppressWarnings("deprecation") static void bootstrap(){
        if(LoadingModList.get()==null)LoadingModList.of(List.of(),List.of(),List.of(),List.of(),Map.of());
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
        var registry=(MappedRegistry<Block>)BuiltInRegistries.BLOCK;
        synchronized(registry){
            registry.unfreeze();
            try{
                lamp=Registry.register(registry,ResourceLocation.parse("myvillage_test:street_rune_lamp"),new RuneLampBlock(StreetFurnitureBlock.properties("rune_lamp")));
                rack=Registry.register(registry,ResourceLocation.parse("myvillage_test:street_drying_rack"),new DryingRackBlock(StreetFurnitureBlock.properties("drying_rack")));
            }finally{registry.freeze();}
        }
    }

    @Test void everyPartAndDirectionResolvesToItsSingleRootAcrossChunkEdges(){
        var root=new BlockPos(15,64,15);
        for(StreetFurnitureBlock block:List.of(lamp,rack)){
            for(Direction facing:List.of(Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST)){
                for(int part=0;part<block.family().parts().size();part++){
                    var state=block.state(part,facing,false);var pos=root.offset(block.family().offset(part,facing));
                    assertEquals(root,block.root(pos,state));assertTrue(block.matches(state,state,part));
                    assertFalse(block.matches(block.state(part,facing.getOpposite(),false),state,part));
                }
            }
        }
    }

    @Test void onlyLampHeadEmitsLightAndRackNeverEmits(){
        assertEquals(0,lamp.state(0,Direction.NORTH,false).getLightEmission());
        assertEquals(14,lamp.state(1,Direction.NORTH,false).getLightEmission());
        assertEquals(14,lamp.state(1,Direction.EAST,true).getLightEmission());
        for(int part=0;part<4;part++)assertEquals(0,rack.state(part,Direction.NORTH,false).getLightEmission());
    }

    @Test void clothIsThinAndCollisionFollowsTheFrame(){
        var rack=StreetFurnitureCatalog.get("drying_rack");assertEquals(2,rack.width());assertEquals(2,rack.height());
        for(var part:rack.parts()){
            assertTrue(part.outline().stream().anyMatch(b->b.z1()-b.z0()<.3));
            assertTrue(part.collision().stream().noneMatch(b->b.z1()-b.z0()<.3));
            for(Direction facing:List.of(Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST)){
                assertFalse(part.collision(facing).isEmpty());
                assertTrue(part.collision(facing).bounds().getYsize()<=1);
            }
        }
    }

    @Test void differentFurnitureNeverSharesOwnership(){
        assertFalse(lamp.matches(rack.defaultBlockState(),lamp.defaultBlockState(),0));
        assertFalse(rack.matches(lamp.defaultBlockState(),rack.defaultBlockState(),0));
        assertThrows(IllegalArgumentException.class,()->lamp.family().part(2));
        assertThrows(IllegalArgumentException.class,()->rack.family().part(4));
    }

    @Test void geometryRejectsNonfiniteAndOutsideCellCoordinates(){
        assertThrows(IllegalArgumentException.class,()->new StreetFurnitureCatalog.Box(0,0,0,Double.NaN,1,1));
        assertThrows(IllegalArgumentException.class,()->new StreetFurnitureCatalog.Box(-1,0,0,1,1,1));
        assertThrows(IllegalArgumentException.class,()->new StreetFurnitureCatalog.Box(0,0,0,17,1,1));
        assertThrows(IllegalArgumentException.class,()->StreetFurnitureCatalog.turn(Direction.UP));
    }
}
