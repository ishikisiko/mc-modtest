package com.example.myvillage.sim;

import com.example.myvillage.worldplan.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlannedSectLedgerTest {
    @Test void genesisAndFutureFoundingUseOnlyReservedLotsAndRejectArbitraryMoves()throws Exception{
        try(var input=WorldPlanRules.class.getResourceAsStream(WorldPlanRules.RESOURCE)){
            var rules=new WorldPlanRules(new String(Objects.requireNonNull(input).readAllBytes(),StandardCharsets.UTF_8));
            SimData.ResourceOpener opener=p->getClass().getResourceAsStream("/"+p);
            var data=WorldSim.loadData(rules.capitals.lifeOpener(opener));
            for(String tier:List.of("small","medium","large")){
                var plan=new WorldPlan(20261102,rules);var sim=WorldSim.genesis(plan.seed(),plan.graph(),data,tier,24);
                assertFalse(sim.sects(false).isEmpty());
                for(int i=0;i<120;i++)sim.step(24);
                Set<String> occupied=new HashSet<>();
                for(var sect:sim.sects(true)){
                    var site=plan.sectAtGate(sect.gateX(),sect.gateZ()).orElseThrow();
                    assertEquals(sect.regionId(),site.regionId());assertTrue(occupied.add(site.id()));
                }
                var first=sim.sects(false).getFirst();byte[] before=sim.toBytes();
                assertThrows(IllegalArgumentException.class,()->sim.moveGate(first.id(),first.gateX()+1,first.gateZ()));
                assertArrayEquals(before,sim.toBytes());
            }
        }
    }
}
