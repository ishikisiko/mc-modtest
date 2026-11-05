package com.example.myvillage.worldplan;

import com.example.myvillage.worldplan.mine.*;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.*;
import java.util.*;
import java.util.function.Function;
import static org.junit.jupiter.api.Assertions.*;

class RegionalMineTest {
    static StarterMineDefinition def;
    @BeforeAll static void bootstrap()throws Exception{
        if(net.neoforged.fml.loading.LoadingModList.get()==null)net.neoforged.fml.loading.LoadingModList.of(List.of(),List.of(),List.of(),List.of(),Map.of());
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();def=WorldPlanTest.rules().starterMine;
    }
    static Function<BlockPos,BlockState> cavity(int radius,BlockState inside){return p->Math.abs(p.getX())<=radius&&Math.abs(p.getZ())<=radius&&p.getY()>=20&&p.getY()<25?inside:Blocks.STONE.defaultBlockState();}
    @Test void connectedNaturalFloorIsRequiredAndSmallPocketRejected(){
        var start=new BlockPos(0,20,0);var large=cavity(16,Blocks.CAVE_AIR.defaultBlockState());
        assertTrue(RegionalMinePlanner.stance(large,start));var points=RegionalMinePlanner.explore(large,start,def.regional);
        assertTrue(RegionalMinePlanner.sufficient(points,start,def.regional));assertTrue(points.size()<=def.regional.scanLimit);
        assertFalse(RegionalMinePlanner.sufficient(RegionalMinePlanner.explore(cavity(2,Blocks.CAVE_AIR.defaultBlockState()),start,def.regional),start,def.regional));
        assertFalse(RegionalMinePlanner.stance(cavity(16,Blocks.WATER.defaultBlockState()),start));
        assertFalse(RegionalMinePlanner.stance(cavity(16,Blocks.LAVA.defaultBlockState()),start));
        assertFalse(RegionalMinePlanner.stance(p->Blocks.CAVE_AIR.defaultBlockState(),start));
    }
    @Test void disconnectedAirDoesNotCountTowardCaveSize(){
        var start=new BlockPos(0,20,0);Function<BlockPos,BlockState> pockets=p->p.getY()>=20&&p.getY()<24&&Math.floorMod(p.getX(),4)<2&&Math.floorMod(p.getZ(),4)<2?Blocks.CAVE_AIR.defaultBlockState():Blocks.STONE.defaultBlockState();
        assertFalse(RegionalMinePlanner.sufficient(RegionalMinePlanner.explore(pockets,start,def.regional),start,def.regional));
    }
    @Test void ordinaryAccessStopsAtNaturalFloorAndSurvivesCodec(){
        var layout=new StarterMineLayout("a".repeat(64),"b".repeat(64),def.hash,"donghai",100,50,200,1,100,200,List.of(),7);
        int end=def.regional.ends.getLast(),floor=def.groundY-def.regional.minDepth;
        var start=new BlockPos(def.entry.x(),floor,end);
        var evidence=RegionalMinePlanner.explore(p->cavity(16,Blocks.CAVE_AIR.defaultBlockState()).apply(p.subtract(start).offset(0,20,0)),start,def.regional);
        var site=new RegionalMineSite(layout,end,floor,evidence);site.requireValid(def);
        var decoded=RegionalMineSite.CODEC.parse(JsonOps.INSTANCE,RegionalMineSite.CODEC.encodeStart(JsonOps.INSTANCE,site).getOrThrow()).getOrThrow();
        assertEquals(site.surface().hash,decoded.surface().hash);assertEquals(site.naturalStances(),decoded.naturalStances());
        var route=site.access(def).stream().filter(p->p.x()==def.entry.x()).toList();
        assertEquals(def.groundY*2,route.getFirst().feet2());assertEquals(floor*2,route.getLast().feet2());
        for(int i=1;i<route.size();i++)assertTrue(Math.abs(route.get(i).feet2()-route.get(i-1).feet2())<=1);
        assertTrue(site.access(def).stream().allMatch(p->p.z()<=end&&Math.abs(p.x()-def.entry.x())<=def.caves.connectionRadius));
        assertFalse(site.reserved(def,layout.worldX(def.entry.x(),end+1),layout.y+floor,layout.worldZ(def.entry.x(),end+1)));
        assertThrows(IllegalArgumentException.class,()->new RegionalMineSite(layout,end,def.groundY-def.regional.maxDepth-1,evidence).requireValid(def));
    }
}
