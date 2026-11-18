package com.example.myvillage.worldplan;

import com.example.myvillage.worldplan.mine.*;
import com.example.myvillage.worldplan.roads.*;
import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.JsonOps;
import java.util.*;
import java.util.function.IntUnaryOperator;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StarterMinePlanTest {
    /** 受控平地只用于选址/接路单元检查；完整原生官道路网另由新世界探针检查。 */
    static WorldPlan flatWorld(long seed)throws Exception{
        var data=JsonParser.parseString(WorldPlanTest.rules().json()).getAsJsonObject();
        for(var value:data.getAsJsonArray("profiles")){var p=value.getAsJsonObject();p.addProperty("base",96);p.addProperty("amplitude",0);}
        data.addProperty("detail_amplitude",0);data.addProperty("ridge_lift",0);data.addProperty("sea_separator_chance",0);
        return new WorldPlan(seed,new WorldPlanRules(data.toString()));
    }
    static RoadLayout road(WorldPlan world,boolean horizontal){
        return road(world,horizontal,side->192);
    }
    private static RoadLayout road(WorldPlan world,boolean horizontal,IntUnaryOperator feet2){
        var city=world.capitalOf(world.spawn().regionId()).orElseThrow();var rules=RoadRules.load();
        int x=city.entrance().x(),z=city.z()+city.bundle().depth+city.margin()+24;
        List<RoadPlan.Cell> cells=new ArrayList<>();List<RoadAStar.Point> points=new ArrayList<>();
        for(int n=0;n<=420;n++){
            int wx=x+(horizontal?n:0),wz=z+(horizontal?0:n);points.add(new RoadAStar.Point(wx,wz));
            for(int k=-rules.width/2;k<=rules.width/2;k++)cells.add(new RoadPlan.Cell(wx+(horizontal?0:k),wz+(horizontal?k:0),feet2.applyAsInt(k),false));
        }
        var slices=RoadPlan.chunks(cells).entrySet().stream().map(e->new RoadLayout.Slice(e.getKey(),e.getValue().stream().mapToInt(RoadLayout::pack).toArray())).toList();
        int[] route=new int[points.size()*2];for(int i=0;i<points.size();i++){route[i*2]=points.get(i).x();route[i*2+1]=points.get(i).z();}
        String other=world.cities().stream().map(c->c.regionId()).filter(id->!id.equals(city.regionId())).findFirst().orElseThrow();
        return new RoadLayout(world.fingerprint(),rules.fingerprint,slices,List.of(new RoadLayout.Route(city.regionId(),other,rules.width,"",route)));
    }
    private static StarterMineLayout mine(WorldPlan world,RoadLayout roads){
        return StarterMinePlanner.search(world,roads,world.rules().starterMine).layout().orElseThrow();
    }
    @Test void mineAndCompleteSpurAreOutsideCitiesAndDoNotOverwriteRoadColumns()throws Exception{
        for(long seed:new long[]{0,1,-1,20261105})for(boolean horizontal:new boolean[]{false,true}){
            var world=flatWorld(seed);var road=road(world,horizontal);var def=world.rules().starterMine;String before=road.hash();
            var mine=mine(world,road);mine.requireIdentity(world,road,def);
            assertEquals(world.spawn().regionId(),mine.region);assertNotEquals("donghai",mine.region);
            assertFalse(world.intersectsCity(mine.minX(def),mine.minZ(def),mine.maxX(def),mine.maxZ(def)));
            for(int z=0;z<def.depth;z++)for(int x=0;x<def.width;x++)assertNull(road.at(mine.worldX(x,z),mine.worldZ(x,z)));
            assertEquals(before,road.hash());assertEquals(mine.hash,mine(world,road).hash);
            var port=mine.position(def.port);assertEquals(port.y()*2,mine.pathAt(port.x(),port.z()).feet2());
            assertNotNull(road.at(mine.anchorX,mine.anchorZ));
            for(var p:mine.path)for(int[] d:new int[][]{{1,0},{0,1}}){var neighbor=mine.pathAt(p.x()+d[0],p.z()+d[1]);if(neighbor!=null)assertTrue(Math.abs(neighbor.feet2()-p.feet2())<=1);}
        }
    }
    @Test void saveRoundTripRetainsLocationAndReadOnlyRoadJoin()throws Exception{
        var world=flatWorld(42);var roads=road(world,false);var mine=mine(world,roads);
        var json=StarterMineLayout.CODEC.encodeStart(JsonOps.INSTANCE,mine).getOrThrow();
        var restored=StarterMineLayout.CODEC.parse(JsonOps.INSTANCE,json).getOrThrow();
        var cached=new StarterMineGeneration(Optional.of(Either.left(restored)),Optional.empty());cached.bind(world,roads);
        var saved=cached.layout().orElseThrow();
        assertEquals(mine.manifest(),saved.manifest());assertEquals(mine.hash,saved.hash);
        assertEquals(mine.path,saved.path);
        assertEquals(mine.caveSeed,cached.caves().seed);
        assertEquals(new StarterMineCavePlan(mine.caveSeed,world.rules().starterMine).manifest(),cached.caves().manifest());
        json.getAsJsonObject().addProperty("x",mine.x+1);
        assertTrue(StarterMineLayout.CODEC.parse(JsonOps.INSTANCE,json).error().isPresent());
    }
    @Test void invalidDefinitionAndMovedCacheAreRejected()throws Exception{
        var world=flatWorld(42);var roads=road(world,false);var def=world.rules().starterMine;
        var mine=mine(world,roads);
        var moved=new StarterMineLayout(mine.worldHash,mine.roadsHash,mine.definitionHash,mine.region,mine.x+32,mine.y,mine.z,mine.rotation,mine.anchorX,mine.anchorZ,mine.path,mine.caveSeed);
        assertThrows(IllegalArgumentException.class,()->moved.requireIdentity(world,roads,def));
        var data=JsonParser.parseString(world.rules().json()).getAsJsonObject();data.getAsJsonObject("starter_mine").addProperty("sha256","0".repeat(64));
        var mismatch=assertThrows(IllegalStateException.class,()->new WorldPlanRules(data.toString()));
        assertTrue(mismatch.getMessage().contains("Frozen capital asset differs"));
    }

    @Test void pavedApproachDoesNotHaveToFitTheSpursEarthworkBudget()throws Exception{
        var world=flatWorld(42);
        var roads=road(world,false,side->210-Math.abs(side));
        var mine=mine(world,roads);
        mine.requireIdentity(world,roads,world.rules().starterMine);
        assertEquals(210,mine.pathAt(mine.anchorX,mine.anchorZ).feet2());
        assertTrue(mine.path.stream().anyMatch(c->roads.at(c.x(),c.z())==null&&c.feet2()<208));
        assertEquals(210,roads.at(mine.anchorX,mine.anchorZ).feet2());
    }

    @Test void blockedStraightAccessCanTurnBeforeTheObstacle()throws Exception{
        var world=flatWorld(42);
        var def=world.rules().starterMine;
        var original=road(world,false);
        var straight=mine(world,original);
        int x=straight.anchorX,z=straight.anchorZ;
        assertTrue(straight.rotation==1||straight.rotation==3);
        int dx=straight.rotation==1?-1:1;
        var cells=new ArrayList<>(original.slices().stream().flatMap(s->s.cells().stream()).toList());
        for(int side=-1;side<=1;side++){
            cells.add(new RoadPlan.Cell(x-3*dx,z+side,193,false,2,0));
            cells.add(new RoadPlan.Cell(x+18*dx,z+side,192,false,3));
        }
        var slices=RoadPlan.chunks(cells).entrySet().stream()
            .map(e->new RoadLayout.Slice(e.getKey(),e.getValue().stream().mapToInt(RoadLayout::pack).toArray())).toList();
        var route=original.routes().getFirst();
        var restricted=new RoadLayout(world.fingerprint(),original.rulesHash(),slices,
            List.of(new RoadLayout.Route(route.from,route.to,route.width,"",new int[]{x,z-1,x,z,x,z+1})));
        var turned=mine(world,restricted);
        var port=turned.position(def.port);
        assertEquals(straight.rotation,turned.rotation);
        assertNotEquals(z,port.z());
        assertNull(turned.pathAt(x+18*dx,z));
        assertTrue(turned.path.size()>def.placement.pathWidth()*(Math.abs(port.x()-x)+1));
        turned.requireIdentity(world,restricted,def);
    }

    @Test void missingAccessIsSavedWithoutInventingAPlacementAndLeavesOtherConsumersUsable()throws Exception{
        var world=flatWorld(42);
        var roads=RoadLayout.empty(world,RoadRules.load());
        var fresh=new StarterMineGeneration(Optional.empty(),Optional.of(List.of()));
        fresh.bind(world,roads);
        var state=fresh.snapshot().orElseThrow();
        var encoded=StarterMineGeneration.CODEC.encodeStart(JsonOps.INSTANCE,state).getOrThrow();
        var restored=StarterMineGeneration.CODEC.parse(JsonOps.INSTANCE,encoded).getOrThrow();
        assertEquals(state,restored);
        var reloaded=new StarterMineGeneration(Optional.of(restored),Optional.of(List.of()));
        reloaded.bind(world,roads);
        reloaded.bindRegional(roads,(x,z)->fail("Unexpected terrain query for saved regional sites"));
        assertTrue(reloaded.layout().isEmpty());
        assertEquals(0,reloaded.shortfall().orElseThrow().attempted());
        assertTrue(reloaded.surfaces().isEmpty());
        assertFalse(reloaded.reserved(0,64,0));
        assertFalse(reloaded.intersects(0,0,0,16,100,16));
        assertThrows(IllegalStateException.class,()->reloaded.bind(flatWorld(43),roads));
        var wrongRoads=new StarterMineGeneration(Optional.of(restored),Optional.empty());
        assertThrows(IllegalStateException.class,()->wrongRoads.bind(world,road(world,false)));
    }
}
