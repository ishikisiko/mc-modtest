package com.example.myvillage.sim;

import static org.junit.jupiter.api.Assertions.*;
import com.example.myvillage.sim.engine.SimContext;
import com.example.myvillage.sim.life.*;
import com.example.myvillage.sim.model.*;
import com.example.myvillage.sim.profession.*;
import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;

/** Pure ledger checks; no claim of real crop blocks, inventory UI or player acceptance. */
class WorldSimProfessionTest {
    static final long SEED=42;
    static final int DAY=24000;
    static final String SITE="herb_town", LEAF="myvillage:spirit_leaf", DEW="myvillage:dew_blossom";
    static final String PILL="myvillage:pill_huiling";
    SimContext context() { return context(SimFixtures.data()); }
    SimContext context(SimData data) {
        var state=new WorldState();state.seed=SEED;state.tierId="small";state.genesisDaysPerYear=24;
        var graph=SimFixtures.graph(SEED);String region=graph.regions().getFirst().id();
        for(var r:graph.regions())state.regions.put(r.id(),new RegionState(r.id()));
        for(int id=1;id<=4;id++) {
            var p=new Person();p.id=id;p.surname="林";p.given="药"+id;p.regionId=region;p.homeRegionId=region;
            p.birthDay=-480;p.root=new int[]{2000,2000,2000,2000,2000};state.persons.put(id,p);
        }
        state.nextPersonId=5;
        var ctx=new SimContext(state,data,graph,24);
        var places=List.of(new LifePlace("home","rest",0,64,0,4,"",0,64,0),
                LifePlace.free("work","work",1,64,0),LifePlace.free("gather_leaf","gather",2,64,0),
                LifePlace.free("gather_dew","gather",3,64,0),LifePlace.free("garden","garden",4,64,0),
                LifePlace.free("market","market",5,64,0));
        LifeEngine.registerSite(ctx,new LifeSite(SITE,"药圃试镇","minecraft:overworld",region,0,64,0,places));
        for(var place:places)LifeEngine.availability(ctx,SITE,place.id(),"available");
        LifeEngine.clock(ctx,0,DAY,false);
        return ctx;
    }
    LifeResident assign(SimContext ctx,int id,String role,String work) {
        assertTrue(LifeEngine.assign(ctx,id,SITE,role,"home",work));return ctx.state.life.residents.get(id);
    }
    WorldSim facade(SimContext ctx) { return WorldSim.fromBytes(StateCodec.toBytes(ctx.state,ctx.realms),ctx.graph,ctx.data); }
    WorldSim reload(WorldSim sim) { return WorldSim.fromBytes(sim.toBytes(),SimFixtures.graph(SEED),SimFixtures.data()); }
    void stock(SimContext ctx,String herb,int units) {
        var s=ctx.state.life.supplies.get(SITE);s.herbStock.put(herb,units);s.stock=ProfessionEngine.total(s.herbStock);
    }
    void prepareGarden(SimContext ctx) { ProfessionEngine.configureGarden(ctx,SITE,"garden",LEAF); }
    void gardenJob(LifeResident r,String activity) { r.activity=activity;r.targetId="garden"; }
    @Test void seedsAreConsumedMaturityIsRequiredAndHarvestIsBoundedAndOnceOnly() {
        var ctx=context();var r=assign(ctx,1,"herb_child","garden");prepareGarden(ctx);stock(ctx,LEAF,1);
        gardenJob(r,"plant");assertTrue(ProfessionEngine.complete(ctx,r,100));
        var supply=ctx.state.life.supplies.get(SITE);var g=ctx.state.life.gardens.get(SITE+"|garden");
        assertEquals(0,supply.stock);assertEquals(0,supply.herbStock.getOrDefault(LEAF,0));
        assertEquals(100+ctx.data.professionRules().growthTicks(),g.matureAt);
        assertFalse(ProfessionEngine.complete(ctx,r,101),"a second planting cannot consume another seed");
        gardenJob(r,"harvest");assertFalse(ProfessionEngine.complete(ctx,r,g.matureAt-1));
        int xp=ctx.state.life.professions.get(1).plantingExperience;
        supply.stock=ctx.data.lifeRules().maxInventory();
        assertFalse(ProfessionEngine.complete(ctx,r,g.matureAt));assertEquals(xp,ctx.state.life.professions.get(1).plantingExperience);
        supply.stock=0;
        assertTrue(ProfessionEngine.complete(ctx,r,g.matureAt));
        assertEquals(ctx.data.professionRules().harvestUnits(),supply.herbStock.get(LEAF));
        assertFalse(ProfessionEngine.complete(ctx,r,10000),"already harvested crop cannot pay again");
        assertEquals(-1,g.plantedAt);assertEquals(-1,g.matureAt);
    }
    @Test void growerCanTendOnceAndAlchemistNeverAcquiresCropPermission() {
        var ctx=context();var grower=assign(ctx,1,"spirit_grower","garden");prepareGarden(ctx);stock(ctx,LEAF,2);
        gardenJob(grower,"plant");assertTrue(ProfessionEngine.complete(ctx,grower,100));
        gardenJob(grower,"tend");assertTrue(ProfessionEngine.complete(ctx,grower,200));
        int xp=ctx.state.life.professions.get(1).plantingExperience;
        assertFalse(ProfessionEngine.complete(ctx,grower,220));assertEquals(xp,ctx.state.life.professions.get(1).plantingExperience);
        var alchemist=assign(ctx,2,"alchemist","work");
        ctx.state.life.professions.get(2).plantingExperience=ctx.data.professionRules().maxExperience();
        assertFalse(ProfessionEngine.canPlant(ctx,alchemist,LEAF));gardenJob(alchemist,"harvest");
        assertFalse(ProfessionEngine.complete(ctx,alchemist,10000));
        gardenJob(grower,"harvest");assertTrue(ProfessionEngine.complete(ctx,grower,10000));
        assertEquals(1+ctx.data.professionRules().harvestUnits()+ctx.data.professionRules().tendedBonus(),ctx.state.life.supplies.get(SITE).stock);
    }
    @Test void exactRecipeRejectsLegacyOrWrongHerbsAndFullShopWithoutExperience() {
        var ctx=context();var r=assign(ctx,1,"alchemist","work");
        com.example.myvillage.sim.commerce.CommerceEngine.initialize(ctx,SITE);
        var recipe=ctx.data.professionRules().recipe("huiling");
        var supply=ctx.state.life.supplies.get(SITE);supply.stock=100;
        assertFalse(ProfessionEngine.canBrew(ctx,r,recipe),"unclassified old stock is not an ingredient");
        stock(ctx,LEAF,10);r.activity="brew";r.recipeId=recipe.id();r.targetId="work";
        assertFalse(ProfessionEngine.complete(ctx,r,400));assertEquals(0,ctx.state.life.professions.get(1).alchemyExperience);
        stock(ctx,DEW,1);
        var market=ctx.state.commerce.markets.get(SITE);var offer=ctx.data.commerceRules().offer("buy_pill_huiling");
        market.items.put(PILL,offer.capacity());
        assertFalse(ProfessionEngine.complete(ctx,r,400));assertEquals(0,ctx.state.life.professions.get(1).alchemyExperience);
        assertEquals(1,supply.herbStock.get(DEW));
        market.items.put(PILL,0);long revision=market.revision;
        assertTrue(ProfessionEngine.complete(ctx,r,400));
        assertEquals(recipe.outputCount(),market.items.get(PILL));assertEquals(revision+1,market.revision);
        assertEquals(0,supply.herbStock.getOrDefault(DEW,0));assertEquals(9,supply.herbStock.get(LEAF));
        assertEquals(ctx.data.professionRules().alchemyExperience(),ctx.state.life.professions.get(1).alchemyExperience);
        assertEquals(0,ctx.state.life.professions.get(1).plantingExperience);
    }
    @Test void explicitGatheringQuotaAndNoTitleBypassOrFailedExperience() {
        var ctx=context();var child=assign(ctx,1,"herb_child","gather_leaf");var place=ctx.state.life.sites.get(SITE).place("gather_leaf").orElseThrow();
        assertFalse(ProfessionEngine.canGather(ctx,child,place,0),"profession needs an explicit typed resource registration");
        assertTrue(ProfessionEngine.registerGathering(ctx,SITE,"gather_leaf",LEAF,1));
        child.activity="gather";child.targetId="gather_leaf";
        assertEquals(1,ProfessionEngine.gather(ctx,child,2,400));
        int xp=ctx.state.life.professions.get(1).gatheringExperience;
        assertEquals(0,ProfessionEngine.gather(ctx,child,2,500));assertEquals(xp,ctx.state.life.professions.get(1).gatheringExperience);
        assertEquals(1,child.herbInventory.get(LEAF));
        assertEquals(1,ProfessionEngine.gather(ctx,child,2,DAY),"only the new day renews this explicit point quota");
        assertEquals(xp+ctx.data.professionRules().gatheringExperience(),ctx.state.life.professions.get(1).gatheringExperience);
        var alchemist=assign(ctx,2,"alchemist","work");ctx.state.life.professions.get(2).gatheringExperience=ctx.data.professionRules().maxExperience();
        assertFalse(ProfessionEngine.canGather(ctx,alchemist,place,DAY));
        assertThrows(IllegalArgumentException.class,()->ProfessionEngine.registerGathering(ctx,SITE,"work",LEAF,1));
        assertThrows(IllegalArgumentException.class,()->ProfessionEngine.configureGarden(ctx,SITE,"garden","myvillage:invented_rare_herb"));
    }
    @Test void higherConfiguredSkillGateAppliesIndependentlyOfRoleAndRealm() throws Exception {
        var input=JsonParser.parseString(Files.readString(Path.of("src/main/resources/data/myvillage/world_sim/herbal_profession_rules.json"))).getAsJsonObject();
        input.getAsJsonObject("herbs").getAsJsonObject(LEAF).addProperty("planting_level",2);
        input.getAsJsonObject("herbs").getAsJsonObject(LEAF).addProperty("gathering_level",2);
        var old=SimFixtures.data();var alchemy=JsonParser.parseString(Files.readString(Path.of("src/main/resources/data/myvillage/alchemy/rules.json"))).getAsJsonObject();
        var rules=ProfessionRules.parse("test",input,alchemy,old.commerceRules());
        var data=new SimData(old.rules(),old.realms(),old.encounters(),old.names(),old.techniques(),old.lore(),old.heritages(),old.sectTasks(),old.lifeRules(),old.commerceRules(),rules);
        var ctx=context(data);var child=assign(ctx,1,"herb_child","garden");ctx.state.persons.get(1).realm=3;
        assertFalse(ProfessionEngine.canPlant(ctx,child,LEAF));
        ProfessionEngine.registerGathering(ctx,SITE,"gather_leaf",LEAF,2);
        var place=ctx.state.life.sites.get(SITE).place("gather_leaf").orElseThrow();
        assertFalse(ProfessionEngine.canGather(ctx,child,place,0));
        var profile=ctx.state.life.professions.get(1);profile.plantingExperience=2*rules.experiencePerLevel();
        assertTrue(ProfessionEngine.canPlant(ctx,child,LEAF));assertFalse(ProfessionEngine.canGather(ctx,child,place,0));
        profile.gatheringExperience=2*rules.experiencePerLevel();assertTrue(ProfessionEngine.canGather(ctx,child,place,0));
        var alchemist=assign(ctx,2,"alchemist","work");ctx.state.life.professions.get(2).plantingExperience=rules.maxExperience();
        assertFalse(ProfessionEngine.canPlant(ctx,alchemist,LEAF));
    }
    @Test void successfulBoundedExperienceSurvivesUnassignReassignmentAndReload() {
        var ctx=context();var r=assign(ctx,1,"herb_child","gather_leaf");
        var profile=ctx.state.life.professions.get(1);profile.gatheringExperience=ctx.data.professionRules().maxExperience()-1;
        ProfessionEngine.registerGathering(ctx,SITE,"gather_leaf",LEAF,2);r.activity="gather";r.targetId="gather_leaf";
        assertEquals(2,ProfessionEngine.gather(ctx,r,2,400));r.herbs=2;
        var sim=facade(ctx);int xp=sim.profession(1).orElseThrow().gatheringExperience();assertEquals(sim.professionRules().maxExperience(),xp);
        assertTrue(sim.unassignLife(1));assertEquals("",sim.profession(1).orElseThrow().role());
        assertTrue(sim.staffProfession(1,SITE,"alchemist","home","work"));
        var loaded=reload(sim);assertEquals(xp,loaded.profession(1).orElseThrow().gatheringExperience());
        assertEquals(0,loaded.profession(1).orElseThrow().alchemyExperience());
        assertEquals("alchemist",loaded.profession(1).orElseThrow().role());
    }
    @Test void realActivityRevisionAndNewLeaseDoNotProduceTwiceAcrossReload() {
        var ctx=context();assign(ctx,1,"alchemist","work");stock(ctx,LEAF,1);stock(ctx,DEW,1);
        com.example.myvillage.sim.commerce.CommerceEngine.initialize(ctx,SITE);
        var sim=facade(ctx);long epoch=sim.activateLife(1);sim.advanceLife(20,DAY);
        var moving=sim.life(1).orElseThrow();assertEquals("brew",moving.activity());
        assertTrue(sim.checkpointLife(1,epoch,1,64,0));assertTrue(sim.arriveLife(1,epoch,moving.revision()));
        sim.advanceLife(120,DAY);assertEquals(0,sim.profession(1).orElseThrow().alchemyExperience());
        assertTrue(sim.releaseLife(1,epoch,1,64,0));sim=reload(sim);long newEpoch=sim.activateLife(1);assertTrue(newEpoch>epoch);
        assertFalse(sim.arriveLife(1,epoch,moving.revision()));
        sim.advanceLife(420,DAY);
        assertEquals(4+sim.professionRules().recipe("huiling").outputCount(),sim.commerce(SITE).orElseThrow().items().get(PILL));
        int xp=sim.profession(1).orElseThrow().alchemyExperience();assertEquals(sim.professionRules().alchemyExperience(),xp);
        byte[] settled=sim.toBytes();assertFalse(sim.advanceLife(420,DAY));assertArrayEquals(settled,sim.toBytes());
        sim.advanceLife(1200,DAY);assertEquals(xp,sim.profession(1).orElseThrow().alchemyExperience());
    }
    @Test void blockedRealActivityAndFullBackpackCannotTrainGathering() {
        var ctx=context();assign(ctx,1,"herb_child","gather_leaf");ProfessionEngine.registerGathering(ctx,SITE,"gather_leaf",LEAF,2);
        var sim=facade(ctx);long epoch=sim.activateLife(1);sim.advanceLife(20,DAY);var r=sim.life(1).orElseThrow();
        assertTrue(sim.checkpointLife(1,epoch,2,64,0));assertTrue(sim.arriveLife(1,epoch,r.revision()));
        sim.setLifePlaceAvailability(SITE,"gather_leaf","blocked");sim.advanceLife(420,DAY);
        assertEquals(0,sim.profession(1).orElseThrow().gatheringExperience());assertTrue(sim.profession(1).orElseThrow().herbInventory().isEmpty());
        var full=context();var child=assign(full,1,"herb_child","gather_leaf");ProfessionEngine.registerGathering(full,SITE,"gather_leaf",LEAF,2);
        child.herbs=full.data.lifeRules().maxInventory();full.state.life.supplies.get(SITE).treasury=0;
        LifeEngine.availability(full,SITE,"home","blocked"); // no rest fees can replenish the empty buyer treasury
        LifeEngine.clock(full,1200,DAY,false);assertEquals(full.data.lifeRules().maxInventory(),child.herbs);
        assertEquals(0,full.state.life.professions.get(1).gatheringExperience);
    }
    @Test void recipesProjectTheirOnlyIngredientCountAndDurationSourceAndRulesRejectUnknownContent() throws Exception {
        var input=JsonParser.parseString(Files.readString(Path.of("src/main/resources/data/myvillage/world_sim/herbal_profession_rules.json"))).getAsJsonObject();
        var alchemy=JsonParser.parseString(Files.readString(Path.of("src/main/resources/data/myvillage/alchemy/rules.json"))).getAsJsonObject();
        var recipe=alchemy.getAsJsonArray("recipes").get(0).getAsJsonObject();
        var counts=new JsonArray();counts.add(2);counts.add(3);recipe.add("ingredient_counts",counts);
        recipe.addProperty("duration_ticks",777);recipe.addProperty("output_count",3);
        var projected=ProfessionRules.parse("test",input,alchemy,SimFixtures.data().commerceRules()).recipe("huiling");
        assertEquals(2,projected.ingredients().get(LEAF));assertEquals(3,projected.ingredients().get(DEW));
        assertEquals(777,projected.durationTicks());assertEquals(3,projected.outputCount());
        input.addProperty("unknown_tuning",1);
        assertThrows(SimDataException.class,()->ProfessionRules.parse("test",input,alchemy,SimFixtures.data().commerceRules()));
        input.remove("unknown_tuning");input.getAsJsonObject("herbs").add("myvillage:rare_invented",new JsonObject());
        assertThrows(SimDataException.class,()->ProfessionRules.parse("test",input,alchemy,SimFixtures.data().commerceRules()));
    }
    @Test void professionReadsAreDetachedAndNeverRegisterOrPromotePeople() {
        var ctx=context();var sim=facade(ctx);byte[] before=sim.toBytes();
        assertTrue(sim.profession(1).isEmpty());assertArrayEquals(before,sim.toBytes());
        assertTrue(sim.staffProfession(1,SITE,"herb_child","home","gather_leaf"));
        var view=sim.profession(1).orElseThrow();byte[] assigned=sim.toBytes();
        assertEquals(0,view.gatheringLevel());assertEquals(0,view.plantingLevel());assertEquals(0,view.alchemyLevel());
        assertThrows(UnsupportedOperationException.class,()->view.herbInventory().put(LEAF,1));
        assertEquals(view,sim.profession(1).orElseThrow());assertArrayEquals(assigned,sim.toBytes());
    }
    @Test void childGatherDeliveryAndGardenFeedAlchemistActualShopInventory() {
        var ctx=context();assign(ctx,1,"herb_child","gather_leaf");assign(ctx,2,"alchemist","work");
        ProfessionEngine.registerGathering(ctx,SITE,"gather_leaf",LEAF,4);ProfessionEngine.registerGathering(ctx,SITE,"gather_dew",DEW,4);
        prepareGarden(ctx);com.example.myvillage.sim.commerce.CommerceEngine.initialize(ctx,SITE);
        var sim=facade(ctx);int initial=sim.commerce(SITE).orElseThrow().items().get(PILL);
        for(long tick=20;tick<=10000;tick+=20)sim.advanceLife(tick,DAY);
        assertTrue(sim.profession(1).orElseThrow().gatheringExperience()>0);
        assertTrue(sim.profession(1).orElseThrow().plantingExperience()>=sim.professionRules().plantingExperience()+sim.professionRules().harvestExperience(),
                "the garden must actually reach a mature harvest");
        assertTrue(sim.profession(2).orElseThrow().alchemyExperience()>0);
        assertTrue(sim.commerce(SITE).orElseThrow().items().get(PILL)>initial);
        assertTrue(sim.lifeSupply(SITE).orElseThrow().gatheredToday()<=8);
        var details=sim.professionSupply(SITE).orElseThrow();assertEquals(0,details.legacyStock());
        assertThrows(UnsupportedOperationException.class,()->details.herbs().put(LEAF,100));
    }
}
