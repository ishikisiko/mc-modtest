package com.example.myvillage.worldplan.roads;

import com.example.myvillage.worldplan.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 只计算固定种子的静态网络，不启动游戏、不读写区块。 */
class SectRoadNetworkSmokeTest {
    @Test void allReservedGatesReachTheTrunkWithoutEditingSectInteriors()throws Exception{
        var raw=getClass().getResourceAsStream("/data/myvillage/worldplan/frozen_v11.json").readAllBytes();
        var world=new WorldPlan(20261103,new WorldPlanRules(new String(raw,java.nio.charset.StandardCharsets.UTF_8)));var rules=RoadRules.load();
        var network=RoadNetworkPlanner.plan(world,rules,List.of(),List.of());
        var layout=RoadLayout.from(world,rules,network);layout.requireIdentity(world,rules);
        assertEquals(world.cities().size()-1+world.sectSites().size(),network.routes().size());
        for(var route:network.routes())if(world.sectSite(route.from()).isPresent()){
            var site=world.sectSite(route.from()).orElseThrow();assertEquals(site.regionId(),route.to());
            assertEquals(rules.sectSpurs.width(),route.width());
            var port=SectRoadPlanner.exit(world,rules,site);
            for(var c:port.existing()){
                var neighbor=layout.at(c.x()+port.frame().dx(),c.z()+port.frame().dz());
                assertNotNull(neighbor);assertTrue(neighbor.band()<2);assertTrue(Math.abs(neighbor.feet2()-c.feet2())<=1);
            }
            for(var c:route.cells())if(!c.existing())assertTrue(SectRoadPlanner.columnAllowed(world,rules,site,c.x(),c.z()));
        }
        var body=new HashMap<Long,RoadPlan.Cell>();for(var c:network.cells())if(!c.existing()&&c.band()<2)body.put(c.key(),c);
        var reached=new HashSet<Long>();var queue=new ArrayDeque<RoadPlan.Cell>();var first=body.values().iterator().next();reached.add(first.key());queue.add(first);
        while(!queue.isEmpty()){
            var c=queue.remove();for(var d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}){var n=body.get(RoadPlan.Cell.key(c.x()+d[0],c.z()+d[1]));if(n!=null&&Math.abs(n.feet2()-c.feet2())<=1&&reached.add(n.key()))queue.add(n);}
        }
        assertEquals(body.size(),reached.size(),"完整道路通行面必须物理连通，不能只靠路线标签宣称接通");
        for(var c:network.cells())if(!c.existing())for(var s:world.sectSites())
            assertFalse(c.x()>=s.x()&&c.x()<s.x()+s.width()&&c.z()>=s.z()&&c.z()<s.z()+s.depth());
    }
}
