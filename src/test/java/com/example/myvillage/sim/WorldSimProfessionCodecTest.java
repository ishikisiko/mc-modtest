package com.example.myvillage.sim;

import static org.junit.jupiter.api.Assertions.*;
import com.example.myvillage.sim.engine.SimContext;
import com.example.myvillage.sim.life.*;
import com.example.myvillage.sim.model.*;
import com.example.myvillage.sim.profession.*;
import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Saved professions migrate explicitly; unknown aggregates never become classified herbs. */
class WorldSimProfessionCodecTest {
    static final long SEED=42;
    static final String SITE="codec_town",LEAF="myvillage:spirit_leaf",DEW="myvillage:dew_blossom";
    SimContext fixture() {
        var state=new WorldState();state.seed=SEED;state.tierId="small";state.genesisDaysPerYear=24;
        var graph=SimFixtures.graph(SEED);String region=graph.regions().getFirst().id();
        for(var r:graph.regions())state.regions.put(r.id(),new RegionState(r.id()));
        var person=new Person();person.id=1;person.surname="林";person.given="存档";person.regionId=region;
        person.homeRegionId=region;person.birthDay=-480;person.root=new int[]{2000,2000,2000,2000,2000};
        state.persons.put(1,person);state.nextPersonId=2;
        var ctx=new SimContext(state,SimFixtures.data(),graph,24);
        LifeEngine.registerSite(ctx,new LifeSite(SITE,"存档试镇","minecraft:overworld",region,0,64,0,List.of(
                LifePlace.free("home","rest",0,64,0),LifePlace.free("work","work",1,64,0),
                LifePlace.free("gather","gather",2,64,0),LifePlace.free("garden","garden",3,64,0))));
        LifeEngine.assign(ctx,1,SITE,"herbalist","home","work");
        LifeEngine.clock(ctx,50000,24000,true);
        var r=state.life.residents.get(1);r.herbs=5;r.herbInventory.put(LEAF,2);
        var supply=state.life.supplies.get(SITE);supply.stock=10;supply.herbStock.put(DEW,3);supply.medicine=7;
        var p=state.life.professions.get(1);p.gatheringExperience=4;p.plantingExperience=5;p.alchemyExperience=6;
        ProfessionEngine.registerGathering(ctx,SITE,"gather",LEAF,4);
        var g=state.life.gathering.get(SITE+"|gather");g.quotaDay=2;g.gatheredToday=2;
        ProfessionEngine.configureGarden(ctx,SITE,"garden",DEW);
        var garden=state.life.gardens.get(SITE+"|garden");garden.plantedAt=49000;garden.matureAt=51400;garden.tended=true;
        com.example.myvillage.sim.commerce.CommerceEngine.initialize(ctx,SITE);
        return ctx;
    }
    JsonObject json(SimContext ctx) {
        return JsonParser.parseString(new String(StateCodec.toBytes(ctx.state,ctx.realms),StandardCharsets.UTF_8)).getAsJsonObject();
    }
    WorldSim restore(JsonObject o) {
        return WorldSim.fromBytes(o.toString().getBytes(StandardCharsets.UTF_8),SimFixtures.graph(SEED),SimFixtures.data());
    }
    JsonObject life(JsonObject root) { return root.getAsJsonObject("life"); }
    @Test void roundtripPreservesClassifiedAndLegacyStocksIdentityExperienceAndMarket() {
        var ctx=fixture();var saved=json(ctx);var sim=restore(saved);
        assertArrayEquals(ctxToBytes(ctx),sim.toBytes());
        assertEquals("herbalist",sim.life(1).orElseThrow().role());
        assertEquals(3,sim.profession(1).orElseThrow().legacyHerbs());
        assertEquals(7,sim.professionSupply(SITE).orElseThrow().legacyStock());
        assertEquals(7,sim.lifeSupply(SITE).orElseThrow().medicine());
        assertEquals(6,sim.profession(1).orElseThrow().alchemyExperience());
        assertEquals(4,sim.commerce(SITE).orElseThrow().items().get("myvillage:pill_huiling"));
        assertEquals(51400,sim.professionSupply(SITE).orElseThrow().gardens().getFirst().matureAt());
    }
    byte[] ctxToBytes(SimContext ctx) { return StateCodec.toBytes(ctx.state,ctx.realms); }
    @Test void schemaOneMigrationKeepsOriginalPeopleRolesAndAggregatesWithoutPromotion() {
        var old=json(fixture());old.addProperty("version",8);var life=life(old);life.addProperty("schema",1);
        // Old versions never owned these fields, even if an input happened to contain them.
        var loaded=restore(old);
        assertEquals("herbalist",loaded.life(1).orElseThrow().role());assertEquals(1,loaded.lifeResidents().size());
        assertEquals(5,loaded.profession(1).orElseThrow().legacyHerbs());assertTrue(loaded.profession(1).orElseThrow().herbInventory().isEmpty());
        assertEquals(0,loaded.profession(1).orElseThrow().alchemyExperience());
        assertEquals(10,loaded.professionSupply(SITE).orElseThrow().legacyStock());assertTrue(loaded.professionSupply(SITE).orElseThrow().herbs().isEmpty());
        assertTrue(loaded.professionSupply(SITE).orElseThrow().gardens().isEmpty());
        assertEquals(4,loaded.commerce(SITE).orElseThrow().items().get("myvillage:pill_huiling"));
        var migrated=JsonParser.parseString(new String(loaded.toBytes(),StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals(9,migrated.get("version").getAsInt());assertEquals(2,life(migrated).get("schema").getAsInt());
    }
    @Test void currentPayloadRequiresNewAccountsAndClassifiedInventoryFields() {
        for(String field:List.of("professions","gathering","gardens")) {
            var broken=json(fixture());life(broken).remove(field);assertThrows(SimFormatException.class,()->restore(broken));
        }
        var resident=json(fixture());life(resident).getAsJsonArray("residents").get(0).getAsJsonObject().remove("herb_inventory");
        assertThrows(SimFormatException.class,()->restore(resident));
        var supply=json(fixture());life(supply).getAsJsonObject("supplies").getAsJsonObject(SITE).remove("herb_stock");
        assertThrows(SimFormatException.class,()->restore(supply));
        var downgraded=json(fixture());life(downgraded).addProperty("schema",1);assertThrows(SimFormatException.class,()->restore(downgraded));
    }
    @Test void rejectsExperienceOverCurrentBoundAndUnknownPersonOrMissingNewRoleProfile() {
        var bad=json(fixture());life(bad).getAsJsonObject("professions").getAsJsonObject("1").addProperty("gathering_experience",SimFixtures.data().professionRules().maxExperience()+1);
        assertThrows(SimFormatException.class,()->restore(bad));
        var unknown=json(fixture());life(unknown).getAsJsonObject("professions").add("99",life(unknown).getAsJsonObject("professions").get("1").deepCopy());
        assertThrows(SimFormatException.class,()->restore(unknown));
        var missing=json(fixture());life(missing).getAsJsonArray("residents").get(0).getAsJsonObject().addProperty("role","alchemist");
        life(missing).getAsJsonObject("professions").remove("1");assertThrows(SimFormatException.class,()->restore(missing));
    }
    @Test void rejectsUnknownNegativeAndExcessClassifiedMaterials() {
        for(String herb:List.of(LEAF,"myvillage:invented_rare_herb")) {
            var broken=json(fixture());var stock=life(broken).getAsJsonObject("supplies").getAsJsonObject(SITE).getAsJsonObject("herb_stock");
            stock.addProperty(herb,11);assertThrows(SimFormatException.class,()->restore(broken));
        }
        var negative=json(fixture());life(negative).getAsJsonArray("residents").get(0).getAsJsonObject().getAsJsonObject("herb_inventory").addProperty(LEAF,-1);
        assertThrows(SimFormatException.class,()->restore(negative));
    }
    @Test void rejectsQuotaOverflowWrongPlaceAndImpossibleGardenState() {
        var quota=json(fixture());life(quota).getAsJsonObject("gathering").getAsJsonObject(SITE+"|gather").addProperty("gathered_today",5);
        assertThrows(SimFormatException.class,()->restore(quota));
        var wrong=json(fixture());var gathering=life(wrong).getAsJsonObject("gathering");gathering.add(SITE+"|work",gathering.remove(SITE+"|gather"));
        assertThrows(SimFormatException.class,()->restore(wrong));
        for(long maturity:new long[]{-1,49000,Long.MAX_VALUE}) {
            var broken=json(fixture());life(broken).getAsJsonObject("gardens").getAsJsonObject(SITE+"|garden").addProperty("mature_at",maturity);
            assertThrows(SimFormatException.class,()->restore(broken));
        }
    }
    @Test void clockRebasePreservesFrozenGardenAndQuotaWithoutRejectingValidSave() {
        var ctx=fixture();LifeEngine.clock(ctx,100,24000,true);var sim=restore(json(ctx));
        assertEquals(51400,sim.professionSupply(SITE).orElseThrow().gardens().getFirst().matureAt());
        assertEquals(2,life(json(ctx)).getAsJsonObject("gathering").getAsJsonObject(SITE+"|gather").get("quota_day").getAsInt());
    }
    @Test void recipesAreTypedAndLockedAcrossEverySavedPhaseAndOldSchemasCannotInventProfessions() {
        var unknown=json(fixture());var r=life(unknown).getAsJsonArray("residents").get(0).getAsJsonObject();
        r.addProperty("activity","brew");r.addProperty("recipe_id","not_a_recipe");assertThrows(SimFormatException.class,()->restore(unknown));
        var dangling=json(fixture());life(dangling).getAsJsonArray("residents").get(0).getAsJsonObject().addProperty("recipe_id","huiling");
        assertThrows(SimFormatException.class,()->restore(dangling));
        var old=json(fixture());old.addProperty("version",8);life(old).addProperty("schema",1);
        life(old).getAsJsonArray("residents").get(0).getAsJsonObject().addProperty("role","alchemist");
        assertThrows(SimFormatException.class,()->restore(old));
    }
}
