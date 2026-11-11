package com.example.myvillage.worldplan.capital;

import com.example.myvillage.worldplan.*;
import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QianjiCapitalTest {
    static WorldPlanRules rules()throws Exception{
        try(var in=QianjiCapitalTest.class.getResourceAsStream(WorldPlanRules.RESOURCE)){
            assertNotNull(in);return new WorldPlanRules(new String(in.readAllBytes(),StandardCharsets.UTF_8));
        }
    }
    @Test void eightCitiesOwnExactlyTheirConfiguredScaleAndTaskBoard()throws Exception{
        var rules=rules();
        for(var city:rules.capitals.bundles){
            var q=city.qianji;int rank=rules.progressionOrder.indexOf(city.regionId);
            assertEquals(rank<4?"compact":rank<7?"large":"headquarters",q.scale());
            assertTrue(city.entries.stream().anyMatch(e->e.id().equals("qianji")&&e.equals(q.entry())));
            var life=city.life().getAsJsonArray("places").asList().stream().map(JsonElement::getAsJsonObject).filter(p->p.get("id").getAsString().equals("jianghu_board")).toList();
            assertEquals(1,life.size());assertEquals(q.board().x(),life.getFirst().getAsJsonArray("block_pos").get(0).getAsInt());
            assertEquals(q.board().y(),q.reader().y());assertEquals(q.board().z()+1,q.reader().z());
            for(long seed:new long[]{20261112,42}){
                var world=new WorldPlan(seed,rules);var placed=world.capitalOf(city.regionId).orElseThrow();
                assertEquals(city.regionId,world.sample(placed.x()+q.reader().x(),placed.z()+q.reader().z()).regionId());
                assertTrue(world.graph().geometry().reserved(placed.x()+q.reader().x(),placed.z()+q.reader().z(),0));
            }
        }
    }
    @Test void changingTheOrganizationDescriptorChangesItsProductionIdentity()throws Exception{
        var bundle=rules().capitals.bundles.getFirst();
        var root=JsonParser.parseString(new String(CapitalGameBundle.readResource(bundle.resource,8*1024*1024),StandardCharsets.UTF_8)).getAsJsonObject();
        var before=CapitalGameBundle.identityJson(root);root.getAsJsonObject("qianji").addProperty("name","changed");
        assertNotEquals(before,CapitalGameBundle.identityJson(root));
    }
}
