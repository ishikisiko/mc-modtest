package com.example.myvillage.sim;

import static org.junit.jupiter.api.Assertions.*;

import com.example.myvillage.sim.commerce.CommerceEngine;
import com.example.myvillage.sim.commerce.CommerceRules;
import com.example.myvillage.sim.engine.SimContext;
import com.example.myvillage.sim.life.*;
import com.example.myvillage.sim.model.*;
import com.example.myvillage.sim.profession.ProfessionEngine;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Ledger capacity checks include old stock without an herb classification. */
class WorldSimHerbCapacityTest {
    private static final long SEED=42;
    private static final String SITE="herb_capacity_town", LEAF="myvillage:spirit_leaf";
    private static final int DAY=24_000;

    private static SimContext context(SimData data) {
        var state=new WorldState();state.seed=SEED;state.tierId="small";state.genesisDaysPerYear=24;
        var graph=SimFixtures.graph(SEED);String region=graph.regions().getFirst().id();
        for(var r:graph.regions())state.regions.put(r.id(),new RegionState(r.id()));
        var person=new Person();person.id=1;person.surname="林";person.given="药";
        person.regionId=region;person.homeRegionId=region;person.birthDay=-480;
        person.root=new int[]{2000,2000,2000,2000,2000};state.persons.put(1,person);state.nextPersonId=2;
        var ctx=new SimContext(state,data,graph,24);
        var places=List.of(new LifePlace("home","rest",0,64,0,1,"",0,64,0),
                LifePlace.free("garden","garden",1,64,0));
        LifeEngine.registerSite(ctx,new LifeSite(SITE,"药圃容量试镇","minecraft:overworld",region,0,64,0,places));
        for(var place:places)LifeEngine.availability(ctx,SITE,place.id(),"available");
        LifeEngine.clock(ctx,0,DAY,false);
        assertTrue(LifeEngine.assign(ctx,1,SITE,"herb_child","home","garden"));
        assertTrue(ProfessionEngine.configureGarden(ctx,SITE,"garden",LEAF));
        return ctx;
    }

    private static long plant(SimContext ctx) {
        var supply=ctx.state.life.supplies.get(SITE);
        supply.stock=ctx.data.professionRules().seedUnits();
        supply.herbStock.put(LEAF,supply.stock);
        var child=ctx.state.life.residents.get(1);
        child.activity="plant";child.targetId="garden";
        assertTrue(ProfessionEngine.complete(ctx,child,100));
        assertEquals(0,supply.stock);
        assertTrue(supply.herbStock.isEmpty());
        return ctx.state.life.gardens.get(SITE+"|garden").matureAt;
    }

    @Test void fullSharedMarketBlocksGardenHarvestAndExperienceUntilEntireYieldFits() {
        var ctx=context(SimFixtures.data());
        assertTrue(CommerceEngine.initialize(ctx,SITE));
        int capacity=ProfessionEngine.stockCapacity(ctx,SITE);
        assertEquals(ctx.data.commerceRules().herbCapacity(-1),capacity);
        long matureAt=plant(ctx);
        var supply=ctx.state.life.supplies.get(SITE);
        var child=ctx.state.life.residents.get(1);
        var garden=ctx.state.life.gardens.get(SITE+"|garden");
        var place=ctx.state.life.sites.get(SITE).place("garden").orElseThrow();
        int plantedExperience=ctx.state.life.professions.get(1).plantingExperience;
        int yield=ctx.data.professionRules().harvestUnits();
        child.activity="harvest";

        supply.stock=capacity; // Every unit is legacy unclassified stock.
        assertEquals(capacity,ProfessionEngine.supplyView(ctx,SITE).orElseThrow().legacyStock());
        assertEquals("",ProfessionEngine.gardenActivity(ctx,child,place,matureAt));
        assertFalse(ProfessionEngine.complete(ctx,child,matureAt));
        supply.stock=capacity-yield+1;
        assertEquals("",ProfessionEngine.gardenActivity(ctx,child,place,matureAt));
        assertFalse(ProfessionEngine.complete(ctx,child,matureAt));
        assertEquals(plantedExperience,ctx.state.life.professions.get(1).plantingExperience);
        assertEquals(100,garden.plantedAt);
        assertEquals(matureAt,garden.matureAt);
        assertTrue(supply.herbStock.isEmpty());

        supply.stock=capacity-yield;
        assertEquals("harvest",ProfessionEngine.gardenActivity(ctx,child,place,matureAt));
        assertTrue(ProfessionEngine.complete(ctx,child,matureAt));
        assertEquals(capacity,supply.stock);
        assertEquals(yield,supply.herbStock.get(LEAF));
        assertEquals(capacity-yield,ProfessionEngine.supplyView(ctx,SITE).orElseThrow().legacyStock());
        assertEquals(plantedExperience+ctx.data.professionRules().harvestExperience(),
                ctx.state.life.professions.get(1).plantingExperience);
        assertEquals(-1,garden.plantedAt);
        assertFalse(ProfessionEngine.complete(ctx,child,matureAt));
    }

    @Test void siteWithoutMarketAndDisabledCommerceKeepLifeInventoryLimit() {
        var activeRules=context(SimFixtures.data());
        int lifeLimit=activeRules.data.lifeRules().maxInventory();
        assertTrue(lifeLimit>activeRules.data.commerceRules().herbCapacity(lifeLimit));
        assertEquals(lifeLimit,ProfessionEngine.stockCapacity(activeRules,SITE));
        long matureAt=plant(activeRules);
        var supply=activeRules.state.life.supplies.get(SITE);
        supply.stock=activeRules.data.commerceRules().herbCapacity(lifeLimit);
        var child=activeRules.state.life.residents.get(1);
        child.activity="harvest";
        assertTrue(ProfessionEngine.complete(activeRules,child,matureAt));
        assertEquals(activeRules.data.commerceRules().herbCapacity(lifeLimit)
                +activeRules.data.professionRules().harvestUnits(),supply.stock);

        var current=SimFixtures.data();
        var disabled=new SimData(current.rules(),current.realms(),current.encounters(),current.names(),
                current.techniques(),current.lore(),current.heritages(),current.sectTasks(),
                current.lifeRules(),CommerceRules.disabled(),current.professionRules());
        var legacy=context(disabled);
        assertTrue(CommerceEngine.initialize(legacy,SITE));
        assertEquals(lifeLimit,ProfessionEngine.stockCapacity(legacy,SITE));
        long oldMaturity=plant(legacy);
        var oldSupply=legacy.state.life.supplies.get(SITE);
        int oldShopCapacity=current.commerceRules().herbCapacity(lifeLimit);
        oldSupply.stock=oldShopCapacity;
        var oldChild=legacy.state.life.residents.get(1);
        oldChild.activity="harvest";
        assertTrue(ProfessionEngine.complete(legacy,oldChild,oldMaturity));
        assertEquals(oldShopCapacity+legacy.data.professionRules().harvestUnits(),oldSupply.stock);
    }
}
