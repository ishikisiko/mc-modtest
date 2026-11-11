package com.example.myvillage.worldplan.roads;

import com.example.myvillage.worldplan.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FixedProgressionRoadTest {
    @Test void roadPlannerRequiresEachConsecutivePairInProgressionOrder()throws Exception {
        WorldPlanRules rules;
        try(var in=getClass().getResourceAsStream(WorldPlanRules.RESOURCE)){
            assertNotNull(in);rules=new WorldPlanRules(new String(in.readAllBytes(),StandardCharsets.UTF_8));
        }
        for(long seed:new long[]{0,42,20261112,Long.MIN_VALUE,Long.MAX_VALUE}){
            var world=new WorldPlan(seed,rules);var borders=RoadNetworkPlanner.progressionBorders(world);
            assertEquals(7,borders.size());
            for(int i=0;i<borders.size();i++)assertEquals(Set.of(rules.progressionOrder.get(i),rules.progressionOrder.get(i+1)),Set.of(borders.get(i).a(),borders.get(i).b()));
        }
    }
}
