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
            assertEquals("entry",life.getFirst().get("kind").getAsString());
            assertEquals(q.board().y(),q.reader().y());
            int dx=q.board().facing().equals("east")?1:q.board().facing().equals("west")?-1:0;
            int dz=q.board().facing().equals("south")?1:q.board().facing().equals("north")?-1:0;
            assertEquals(q.board().x()+dx,q.reader().x());assertEquals(q.board().z()+dz,q.reader().z());
            assertEquals(rank==7?"east":"south",q.entry().facing());
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
    @Test void zhongzhouHeadquartersUsesTheApprovedResidentialPlotInsideTheOriginalCity()throws Exception{
        var city=rules().capitals.bundles.stream().filter(c->c.regionId.equals("zhongzhou")).findFirst().orElseThrow();
        assertEquals(1200,city.width);assertEquals(320,city.height);assertEquals(1440,city.depth);
        var root=JsonParser.parseString(new String(CapitalGameBundle.readResource(city.resource,8*1024*1024),StandardCharsets.UTF_8)).getAsJsonObject();
        var infill=root.getAsJsonObject("qianji").getAsJsonObject("infill");
        assertEquals("zhongzhou_west_infill",infill.get("id").getAsString());
        assertEquals(List.of(365,946,524,1116),infill.getAsJsonArray("plot").asList().stream().map(JsonElement::getAsInt).toList());
        var retired=infill.getAsJsonArray("retired_buildings").asList().stream().map(JsonElement::getAsString).collect(java.util.stream.Collectors.toSet());
        assertEquals(73,retired.size());assertTrue(infill.get("maximum_terrain_cut").getAsInt()<=3);
        assertTrue(root.getAsJsonArray("buildings").asList().stream().map(JsonElement::getAsJsonObject).noneMatch(b->retired.contains(b.get("id").getAsString())));
        assertEquals("east",city.qianji.entry().facing());
    }
}
