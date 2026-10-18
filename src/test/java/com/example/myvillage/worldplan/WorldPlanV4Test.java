package com.example.myvillage.worldplan;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;

class WorldPlanV4Test {
    @Test void verifiedOlderLayoutCanRegisterFacilitiesButUnknownIdentityCannot() throws Exception {
        JsonObject layout;
        try(var in=WorldPlanV4Test.class.getResourceAsStream("/data/myvillage/npc_life/places.json")) {
            assertNotNull(in);
            var layouts=JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("layouts");
            layout=java.util.stream.StreamSupport.stream(layouts.spliterator(),false).map(JsonElement::getAsJsonObject)
                    .filter(x->x.get("id").getAsString().equals("yunshan_planned_v4")).findFirst().orElseThrow();
        }
        var current=load(WorldPlanRules.RESOURCE).city.layoutHash();
        assertTrue(com.example.myvillage.sim.runtime.life.NpcLifeSites.matchesLayout(layout,current));
        var aliases=layout.getAsJsonArray("compatible_worldplan_layout_hashes");assertFalse(aliases.isEmpty());
        for(var alias:aliases) {
            assertNotEquals(current,alias.getAsString());
            assertTrue(com.example.myvillage.sim.runtime.life.NpcLifeSites.matchesLayout(layout,alias.getAsString()));
        }
        assertFalse(com.example.myvillage.sim.runtime.life.NpcLifeSites.matchesLayout(layout,"f".repeat(64)));
        var strict=layout.deepCopy();strict.remove("compatible_worldplan_layout_hashes");
        assertFalse(com.example.myvillage.sim.runtime.life.NpcLifeSites.matchesLayout(strict,aliases.get(0).getAsString()));
        assertTrue(com.example.myvillage.sim.runtime.life.NpcLifeSites.matchesLayout(strict,current));
    }
    static WorldPlanRules load(String resource) throws IOException {
        try(var in=WorldPlanV4Test.class.getResourceAsStream(resource)) {
            assertNotNull(in,resource);return new WorldPlanRules(new String(in.readAllBytes(),StandardCharsets.UTF_8));
        }
    }
    @Test void currentDefaultContainsTheWholeDistrictAndCorrectLifeAdapter() throws Exception {
        var r=load(WorldPlanRules.RESOURCE);
        assertEquals("myvillage:worldplan_v4",r.algorithm);assertEquals("yunshan_planned_v4",r.city.lifeLayout());
        assertEquals(224,r.city.width());assertEquals(272,r.city.depth());assertEquals(82,r.city.height());
        assertEquals(238,r.city.tiles().size());assertTrue(r.city.tiles().stream().allMatch(t->t.template().contains("/yunshan_v4/")));
        assertEquals(r.city.width()*r.city.depth(),r.city.surfaces().length);
        assertTrue(Arrays.stream(r.city.surfaces()).distinct().count()>5,"not a flat replacement for the approved terraces");
    }
    @Test void frozenV4FitsModifiedUtfAndDecodesWithoutChangingThePlan() throws Exception {
        var r=load(WorldPlanRules.RESOURCE);String encoded=FrozenRulesEncoding.encode(r.json());
        assertTrue(encoded.length()<65000);var bytes=new ByteArrayOutputStream();new DataOutputStream(bytes).writeUTF(encoded);
        assertEquals(encoded,new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())).readUTF());
        var restored=new WorldPlanRules(FrozenRulesEncoding.decode(encoded));assertEquals(r.json(),restored.json());
        assertEquals(new WorldPlan(-20261018,r).manifest(),new WorldPlan(-20261018,restored).manifest());
        assertThrows(IllegalArgumentException.class,()->FrozenRulesEncoding.decode(r.json()));
        assertThrows(IllegalArgumentException.class,()->FrozenRulesEncoding.decode("gzip:broken"));
        assertThrows(IllegalArgumentException.class,()->FrozenRulesEncoding.encode("x".repeat(4*1024*1024+1)));
    }
    @Test void existingThreeAlgorithmsKeepTheirCityResourcesAndRoundTrip() throws Exception {
        for(String resource:List.of(WorldPlanRules.LEGACY_RESOURCE,WorldPlanRules.V2_RESOURCE,WorldPlanRules.V3_RESOURCE)) {
            var r=load(resource);assertEquals(35,r.city.tiles().size());assertEquals("yunshan_planned_v1",r.city.lifeLayout());
            assertTrue(r.city.tiles().stream().allMatch(t->t.template().contains("/yunshan_v1/")));
            assertEquals(0,r.city.surfaces().length);
            for(long seed:new long[]{1,-42,20261017})assertEquals(new WorldPlan(seed,r).manifest(),new WorldPlan(seed,new WorldPlanRules(r.json())).manifest());
        }
    }
    @Test void everyColumnUsesTheApprovedTwoDimensionalSurface() throws Exception {
        var r=load(WorldPlanRules.RESOURCE);var p=new WorldPlan(20261017,r);var city=p.city();
        for(int z=0;z<city.depth();z++)for(int x=0;x<city.width();x++)
            assertEquals(city.baseY()+r.city.surface(x,z),p.surfaceY(city.x()+x,city.z()+z),"column "+x+","+z);
    }
    @Test void missingOrOverflowingSurfaceRunsFailInsteadOfInventingTerrain() throws Exception {
        var r=load(WorldPlanRules.RESOURCE);var json=JsonParser.parseString(r.json()).getAsJsonObject();
        var runs=json.getAsJsonObject("city").getAsJsonObject("layout").getAsJsonArray("surface_runs");
        runs.remove(runs.size()-1);assertThrows(IllegalArgumentException.class,()->new WorldPlanRules(json.toString()));
        runs.get(0).getAsJsonArray().set(0,new JsonPrimitive(Integer.MAX_VALUE));
        assertThrows(IllegalArgumentException.class,()->new WorldPlanRules(json.toString()));
    }
}
