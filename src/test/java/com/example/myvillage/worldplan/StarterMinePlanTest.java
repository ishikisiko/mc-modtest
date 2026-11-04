package com.example.myvillage.worldplan;

import com.example.myvillage.worldplan.mine.*;
import com.example.myvillage.worldplan.roads.*;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StarterMinePlanTest {
    /** 受控平地只用于选址/接路单元检查；完整原生官道路网另由新世界探针检查。 */
    private static WorldPlan flatWorld(long seed)throws Exception{
        var data=JsonParser.parseString(WorldPlanTest.rules().json()).getAsJsonObject();
        for(var value:data.getAsJsonArray("profiles")){var p=value.getAsJsonObject();p.addProperty("base",96);p.addProperty("amplitude",0);}
        data.addProperty("detail_amplitude",0);data.addProperty("ridge_lift",0);data.addProperty("sea_separator_chance",0);
        return new WorldPlan(seed,new WorldPlanRules(data.toString()));
    }
    private static RoadLayout road(WorldPlan world,boolean horizontal){
        var city=world.capitalOf(world.spawn().regionId()).orElseThrow();var rules=RoadRules.load();
        int x=city.entrance().x(),z=city.z()+city.bundle().depth+city.margin()+24;
        List<RoadPlan.Cell> cells=new ArrayList<>();List<RoadAStar.Point> points=new ArrayList<>();
        for(int n=0;n<=420;n++){
            int wx=x+(horizontal?n:0),wz=z+(horizontal?0:n);points.add(new RoadAStar.Point(wx,wz));
            for(int k=-rules.width/2;k<=rules.width/2;k++)cells.add(new RoadPlan.Cell(wx+(horizontal?0:k),wz+(horizontal?k:0),192,false));
        }
        var slices=RoadPlan.chunks(cells).entrySet().stream().map(e->new RoadLayout.Slice(e.getKey(),e.getValue().stream().mapToInt(RoadLayout::pack).toArray())).toList();
        int[] route=new int[points.size()*2];for(int i=0;i<points.size();i++){route[i*2]=points.get(i).x();route[i*2+1]=points.get(i).z();}
        String other=world.cities().stream().map(c->c.regionId()).filter(id->!id.equals(city.regionId())).findFirst().orElseThrow();
        return new RoadLayout(world.fingerprint(),rules.fingerprint,slices,List.of(new RoadLayout.Route(city.regionId(),other,rules.width,"",route)));
    }
    @Test void mineAndCompleteSpurAreOutsideCitiesAndDoNotOverwriteRoadColumns()throws Exception{
        for(long seed:new long[]{0,1,-1,20261105})for(boolean horizontal:new boolean[]{false,true}){
            var world=flatWorld(seed);var road=road(world,horizontal);var def=world.rules().starterMine;String before=road.hash();
            var mine=StarterMinePlanner.plan(world,road,def);mine.requireIdentity(world,road,def);
            assertEquals(world.spawn().regionId(),mine.region);assertNotEquals("donghai",mine.region);
            assertFalse(world.intersectsCity(mine.minX(def),mine.minZ(def),mine.maxX(def),mine.maxZ(def)));
            for(int z=0;z<def.depth;z++)for(int x=0;x<def.width;x++)assertNull(road.at(mine.worldX(x,z),mine.worldZ(x,z)));
            assertEquals(before,road.hash());assertEquals(mine.hash,StarterMinePlanner.plan(world,road,def).hash);
            var port=mine.position(def.port);assertEquals(port.y()*2,mine.pathAt(port.x(),port.z()).feet2());
            assertNotNull(road.at(mine.anchorX,mine.anchorZ));
            for(var p:mine.path)for(int[] d:new int[][]{{1,0},{0,1}}){var neighbor=mine.pathAt(p.x()+d[0],p.z()+d[1]);if(neighbor!=null)assertTrue(Math.abs(neighbor.feet2()-p.feet2())<=1);}
        }
    }
    @Test void saveRoundTripRetainsLocationAndReadOnlyRoadJoin()throws Exception{
        var world=flatWorld(42);var roads=road(world,false);var mine=StarterMinePlanner.plan(world,roads,world.rules().starterMine);
        var json=StarterMineLayout.CODEC.encodeStart(JsonOps.INSTANCE,mine).getOrThrow();
        var restored=StarterMineLayout.CODEC.parse(JsonOps.INSTANCE,json).getOrThrow();
        var cached=new StarterMineGeneration(Optional.of(restored));cached.bind(world,roads);
        assertEquals(mine.manifest(),cached.layout().manifest());assertEquals(mine.hash,cached.layout().hash);
        assertEquals(mine.path,cached.layout().path);
        json.getAsJsonObject().addProperty("x",mine.x+1);
        assertTrue(StarterMineLayout.CODEC.parse(JsonOps.INSTANCE,json).error().isPresent());
    }
    @Test void invalidDefinitionMissingRoadAndMovedCacheAreRejected()throws Exception{
        var world=flatWorld(42);var roads=road(world,false);var def=world.rules().starterMine;
        assertThrows(IllegalStateException.class,()->StarterMinePlanner.plan(world,RoadLayout.empty(world,RoadRules.load()),def));
        var mine=StarterMinePlanner.plan(world,roads,def);
        var moved=new StarterMineLayout(mine.worldHash,mine.roadsHash,mine.definitionHash,mine.region,mine.x+32,mine.y,mine.z,mine.rotation,mine.anchorX,mine.anchorZ,mine.path);
        assertThrows(RuntimeException.class,()->moved.requireIdentity(world,roads,def));
        var data=JsonParser.parseString(world.rules().json()).getAsJsonObject();data.getAsJsonObject("starter_mine").addProperty("sha256","0".repeat(64));
        assertThrows(IllegalArgumentException.class,()->new WorldPlanRules(data.toString()));
    }
}
