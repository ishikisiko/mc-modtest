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
    @Test void deepConnectionOutsideSurfaceFootprintRetainsChunkOwnershipAndProtection()throws Exception{
        StarterMineCaveTest.bootstrap();var world=StarterMinePlanTest.flatWorld(42);
        for(int rotation=0;rotation<4;rotation++){
            var layout=new StarterMineLayout("a".repeat(64),"b".repeat(64),def.hash,"lingyue",200,120,300,rotation,200,300,List.of(),7);
            int end=def.regional.ends.getLast(),floor=def.groundY-96;var start=new BlockPos(def.entry.x(),floor,end);
            var evidence=RegionalMinePlanner.explore(p->cavity(16,Blocks.CAVE_AIR.defaultBlockState()).apply(p.subtract(start).offset(0,20,0)),start,def.regional);
            var site=new RegionalMineSite(layout,end,floor,evidence);site.requireValid(def);
            int x=layout.worldX(start.getX(),start.getZ()),y=layout.y+floor,z=layout.worldZ(start.getX(),start.getZ());
            assertFalse(layout.inside(def,x,z));assertTrue(site.reserved(def,x,y,z));
            assertTrue(site.intersects(world,def,x,y,z,x,y,z));assertFalse(site.intersects(world,def,x,y+def.caves.headroom+def.caves.seal,z,x,y+def.caves.headroom+def.caves.seal+1,z));
            var chunk=new StarterMineCaveTest.TerrainChunk(new net.minecraft.world.level.ChunkPos(x>>4,z>>4),Blocks.STONE.defaultBlockState());
            site.finish(def,chunk);assertTrue(chunk.getBlockState(new BlockPos(x,y,z)).isAir());assertTrue(chunk.getBlockState(new BlockPos(x,y-1,z)).is(Blocks.STONE));
            var lighting=StarterMineCaveTest.lights(layout,site.torches(def));
            chunk.writes.forEach((pos,state)->{
                if(lighting.containsKey(pos))assertEquals(lighting.get(pos),state);
                else assertTrue(state.isAir()||state.is(Blocks.STONE)||state.is(Blocks.STONE_SLAB));
            });
            chunk.setPersistedStatus(net.minecraft.world.level.chunk.status.ChunkStatus.FULL);var before=Map.copyOf(chunk.writes);
            assertThrows(IllegalStateException.class,()->site.finish(def,chunk));assertEquals(before,chunk.writes);
        }
    }

    @Test void continuousLiningSealsWetCarverBoundariesInEitherChunkOrder()throws Exception{
        StarterMineCaveTest.bootstrap();
        for(int rotation=0;rotation<4;rotation++){
            var layout=new StarterMineLayout("a".repeat(64),"b".repeat(64),def.hash,"lingyue",200,120,300,rotation,200,300,List.of(),7);
            int end=def.regional.ends.getLast(),floor=def.groundY-96;var start=new BlockPos(def.entry.x(),floor,end);
            var evidence=RegionalMinePlanner.explore(p->cavity(16,Blocks.CAVE_AIR.defaultBlockState()).apply(p.subtract(start).offset(0,20,0)),start,def.regional);
            var site=new RegionalMineSite(layout,end,floor,evidence);site.requireValid(def);
            var lighting=StarterMineCaveTest.lights(layout,site.torches(def));
            assertFalse(site.reserved(def,layout.worldX(def.entry.x()+def.caves.connectionRadius+1,end),layout.y+floor,layout.worldZ(def.entry.x()+def.caves.connectionRadius+1,end)),"洞体交接处必须保留侧向出口");
            List<net.minecraft.world.level.ChunkPos> order=new ArrayList<>();
            for(int z=site.minZ(def)>>4;z<=site.maxZ(def)>>4;z++)for(int x=site.minX(def)>>4;x<=site.maxX(def)>>4;x++)order.add(new net.minecraft.world.level.ChunkPos(x,z));
            Map<BlockPos,BlockState> first=null;
            for(int pass=0;pass<2;pass++){
                Map<BlockPos,BlockState> actual=new HashMap<>();
                for(var chunkPos:order){var chunk=new StarterMineCaveTest.TerrainChunk(chunkPos,Blocks.WATER.defaultBlockState());site.finish(def,chunk);actual.putAll(chunk.writes);}
                StarterMineCaveTest.assertPairedLighting(def,layout,site.torches(def),end-def.caves.connectionRadius-def.caves.seal,actual);
                for(var cell:site.access(def)){
                    if(!site.lined(def,cell.z()-1)||!site.lined(def,cell.z()+1))continue; // 两端是连接地面和天然洞体的开放口。
                    int x=layout.worldX(cell.x(),cell.z()),z=layout.worldZ(cell.x(),cell.z()),head=layout.y+(int)Math.ceil(cell.feet2()/2.0)+def.caves.headroom-1;
                    for(int y=layout.y+cell.floorY()+1;y<=head;y++){
                        var at=new BlockPos(x,y,z);assertEquals(lighting.getOrDefault(at,Blocks.CAVE_AIR.defaultBlockState()),actual.get(at));
                        for(var direction:net.minecraft.core.Direction.values())assertTrue(actual.getOrDefault(at.relative(direction),Blocks.WATER.defaultBlockState()).getFluidState().isEmpty(),at+" "+direction);
                    }
                    for(int y=head+1;y<=head+def.caves.seal;y++){assertTrue(actual.get(new BlockPos(x,y,z)).is(Blocks.STONE));assertTrue(site.reserved(def,x,y,z));}
                }
                if(first==null)first=Map.copyOf(actual);else assertEquals(first,actual);
                Collections.reverse(order);
            }
        }
    }

}
