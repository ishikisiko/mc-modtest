package com.example.myvillage.worldplan.roads;
import com.example.myvillage.worldplan.*;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class RoadNetworkTest {
    @Test void fourDirectionsLeaveCityFromTheCorrectEdge(){
        for(var facing:RoadPortGeometry.Facing.values()){
            var p=new RoadRules.Port("a","capital_a",10,20,80,64,80,3,facing,List.of(new RoadRules.LocalFloor(10,20,160)));
            var f=RoadPortGeometry.frame(p,100,200,24,5,8);assertTrue(f.permits(f.edgeX(),f.edgeZ(),2));assertFalse(f.permits(f.edgeX()-f.dx(),f.edgeZ()-f.dz(),2));
            assertEquals(facing.dx,f.dx());assertEquals(facing.dz,f.dz());assertEquals(f.edgeX()+f.dx()*5,f.outside(5).x());
        }
    }
    @Test void junctionLocksExistingCarriagewayAndReportsImpossibleFit(){
        var old=List.of(new RoadPlan.Cell(0,0,160,false));var cells=List.of(new RoadPlan.Cell(0,0,162,false),new RoadPlan.Cell(1,0,163,false));
        var fitted=RoadNetworkGeometry.fit(cells,old,List.of(),(x,z)->80,8);assertEquals(160,fitted.get(0).feet2());assertEquals(161,fitted.get(1).feet2());
        assertThrows(IllegalStateException.class,()->RoadNetworkGeometry.fit(cells,List.of(new RoadPlan.Cell(0,0,200,false)),List.of(),(x,z)->80,8));
    }
    @Test @Timeout(600) void allEightCapitalsAreConnectedThroughFormalPortsAndOneMoyuPass()throws Exception{
        WorldPlan world;try(var in=getClass().getResourceAsStream(WorldPlanRules.RESOURCE)){assertNotNull(in);world=new WorldPlan(20261101,new WorldPlanRules(new String(in.readAllBytes(),StandardCharsets.UTF_8)));}
        var rules=RoadRules.load();assertEquals(8,rules.ports.size());assertEquals(1,world.borders().stream().filter(WorldPlan.Border::gate).count());
        var gate=world.borders().stream().filter(WorldPlan.Border::gate).findFirst().orElseThrow();
        var crossing=world.geometry().unwarp(gate.crossing().x(),gate.crossing().z());assertTrue(WorldRoadPlanner.legalBorderCrossing(world,rules,gate.a(),gate.b(),(int)Math.round(crossing[0]),(int)Math.round(crossing[1])));
        var outside=world.geometry().unwarp(gate.p().x()*.8+gate.q().x()*.2,gate.p().z()*.8+gate.q().z()*.2);assertFalse(WorldRoadPlanner.legalBorderCrossing(world,rules,gate.a(),gate.b(),(int)Math.round(outside[0]),(int)Math.round(outside[1])));
        var result=RoadNetworkPlanner.plan(world,rules,List.of(),List.of());assertEquals(7,result.routes().size());
        Set<String> reached=new HashSet<>(Set.of("zhongzhou"));for(int i=0;i<8;i++)for(var r:result.routes()){if(reached.contains(r.from()))reached.add(r.to());if(reached.contains(r.to()))reached.add(r.from());}assertEquals(rules.ports.keySet(),reached);
        var merged=RoadNetworkGeometry.index(result.cells());
        for(var route:result.routes())for(var c:route.cells())if(c.existing()||c.band()<2){var after=merged.get(c.key());assertNotNull(after);assertEquals(c.feet2(),after.feet2());assertEquals(c.existing(),after.existing());if(!c.existing())assertTrue(after.band()<2);}
        var out=new LinkedHashMap<String,Object>();out.put("seed",world.seed());out.put("world_hash",world.fingerprint());out.put("rules_hash",rules.fingerprint);out.put("cities",world.cities().stream().map(c->Map.of("region",c.regionId(),"x",c.x(),"z",c.z(),"width",c.bundle().width,"depth",c.bundle().depth)).toList());
        out.put("routes",result.routes().stream().map(r->Map.of("from",r.from(),"to",r.to(),"centerline",r.centerline(),"columns",r.cells().size())).toList());out.put("columns",result.cells().size());out.put("chunks",RoadPlan.chunks(result.cells()).size());
        var path=Path.of("build/reports/full-network.json");Files.createDirectories(path.getParent());Files.writeString(path,new GsonBuilder().setPrettyPrinting().create().toJson(out));
    }
}
