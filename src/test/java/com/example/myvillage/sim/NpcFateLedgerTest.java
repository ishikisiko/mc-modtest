package com.example.myvillage.sim;

import com.example.myvillage.sim.engine.*;
import com.example.myvillage.sim.model.*;
import com.example.myvillage.sim.fate.NpcFatePool;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NpcFateLedgerTest {
    private SimContext context() {
        var state=new WorldState();state.seed=21;state.tierId="small";state.genesisDaysPerYear=6;
        return new SimContext(state,SimFixtures.data(),SimFixtures.graph(21),6);
    }
    private Person create(SimContext ctx) {
        return People.create(ctx,ctx.rng(1,Purpose.GENESIS_PERSON),new int[]{300,0,0,0,0},0,0,10,
                ctx.graph.regions().getFirst().id());
    }
    @Test void everyGenesisPersonHasPersistentFatesAndRestartDoesNotReroll() {
        var sim=SimFixtures.genesis(21,"small",6);
        byte[] saved=sim.toBytes();
        var restored=WorldSim.fromBytes(saved,SimFixtures.graph(21),SimFixtures.data());
        for(var person:sim.findPersons("",Integer.MAX_VALUE)) {
            var roll=sim.npcFates(person.id()).orElseThrow();
            assertEquals(roll,restored.npcFates(person.id()).orElseThrow());
            assertEquals(person.prodigy(),roll.spent()==roll.budget());
        }
        assertArrayEquals(saved,restored.toBytes());
    }
    @Test void cultivationRefreshRootGrowthAndDeathRetainBirthRoll() {
        var ctx=context();var person=create(ctx);var born=person.fates;
        ctx.state.day=500;
        person.root=new int[]{300,200,40,75,10};
        NpcCultivation.initialize(ctx,person);
        NpcCultivation.reached(ctx,person);
        assertSame(born,person.fates);
        Deaths.bury(ctx,person,Deaths.OLD_AGE,-1,-1);
        assertEquals(born,ctx.state.tombstones.get(person.id).fates);
        var restored=StateCodec.fromBytes(StateCodec.toBytes(ctx.state,ctx.realms),ctx.realms);
        assertEquals(born,restored.tombstones.get(person.id).fates);
    }
    @Test void currentLedgerRequiresTheCompleteBudgetedBirthRecord() {
        var ctx=context();create(ctx);
        for(String key:new String[]{"fates","budget","spent","selected"}) {
            var json=StateCodec.toJson(ctx.state,ctx.realms);
            var person=json.getAsJsonArray("persons").get(0).getAsJsonObject();
            if(key.equals("fates"))person.remove(key);else person.getAsJsonObject("fates").remove(key);
            assertThrows(SimFormatException.class,()->StateCodec.fromBytes(json.toString().getBytes(StandardCharsets.UTF_8),ctx.realms));
        }
        var json=StateCodec.toJson(ctx.state,ctx.realms);
        var person=json.getAsJsonArray("persons").get(0).getAsJsonObject();
        person.getAsJsonObject("fates").addProperty("spent",person.get("prodigy").getAsBoolean()?8:9);
        assertThrows(SimFormatException.class,()->StateCodec.fromBytes(json.toString().getBytes(StandardCharsets.UTF_8),ctx.realms));
    }
    @Test void savedBudgetAndIdsSurviveAChangedFuturePool() {
        var sim=SimFixtures.genesis(21,"small",6);
        var d=SimFixtures.data();
        var changed=new SimData(d.rules(),d.realms(),d.encounters(),d.names(),d.techniques(),d.lore(),
                d.heritages(),d.sectTasks(),d.lifeRules(),d.commerceRules(),d.professionRules(),new NpcFatePool(10,d.npcFates().entries()));
        var restored=WorldSim.fromBytes(sim.toBytes(),SimFixtures.graph(21),changed);
        for(var person:sim.findPersons("",Integer.MAX_VALUE))
            assertEquals(sim.npcFates(person.id()),restored.npcFates(person.id()));
    }
    @Test void runtimeCooldownsFollowThePersonAndRejectStaleBodiesAndWrongSelections() {
        var sim=SimFixtures.genesis(21,"small",6);int id=sim.findPersons("",1).getFirst().id();
        long epoch=sim.claimCombat(id,new com.example.myvillage.sim.combat.NpcCombatProfile(20,20,"",0,0,"",0,null));
        var roll=sim.npcFates(id).orElseThrow();var runtime=new com.google.gson.JsonObject();
        runtime.add("selected",roll.toJson().get("selected"));runtime.add("candidates",roll.toJson().get("selected"));
        runtime.addProperty("confirmed",true);runtime.addProperty("hunter_ready_at",123456);
        String saved=runtime.toString();
        assertTrue(sim.replaceNpcFateRuntime(id,epoch,"",saved));
        assertFalse(sim.replaceNpcFateRuntime(id,epoch+1,saved,saved));
        assertFalse(sim.replaceNpcFateRuntime(id,epoch,"",saved));
        assertTrue(sim.releaseCombat(id,epoch));
        var restored=WorldSim.fromBytes(sim.toBytes(),SimFixtures.graph(21),SimFixtures.data());
        assertEquals(saved,restored.npcFateRuntime(id).orElseThrow());
        long next=restored.claimCombat(id,new com.example.myvillage.sim.combat.NpcCombatProfile(20,20,"",0,0,"",0,null));
        assertTrue(next>epoch);assertFalse(restored.replaceNpcFateRuntime(id,epoch,saved,saved));
        runtime.getAsJsonArray("selected").set(0,new com.google.gson.JsonPrimitive("myvillage:unexpected"));
        assertThrows(IllegalArgumentException.class,()->restored.replaceNpcFateRuntime(id,next,saved,runtime.toString()));
        assertEquals(saved,restored.npcFateRuntime(id).orElseThrow());
    }
    @Test void pendingEmberDoesNotEarnColdCultivationAfterItsBodyIsWithdrawn() {
        var sim=SimFixtures.genesis(21,"small",6);
        var person=sim.findPersons("",Integer.MAX_VALUE).stream().filter(p->p.alive()&&p.realmId().equals("qi_refining"))
                .max(java.util.Comparator.comparingLong(PersonView::birthDay)).orElseThrow();
        int id=person.id();var roll=sim.npcFates(id).orElseThrow();
        var saved=new com.google.gson.JsonObject();saved.addProperty("confirmed",true);
        saved.add("selected",roll.toJson().get("selected"));saved.add("candidates",roll.toJson().get("selected"));
        var effects=new com.google.gson.JsonObject();effects.add("phoenix_ember",new com.google.gson.JsonObject());saved.add("npc_effects",effects);
        long epoch=sim.claimCombat(id,new com.example.myvillage.sim.combat.NpcCombatProfile(20,20,"",0,0,"",0,null));
        assertTrue(sim.replaceNpcFateRuntime(id,epoch,"",saved.toString()));assertTrue(sim.releaseCombat(id,epoch));
        sim.step(6);
        assertEquals(person.progress(),sim.person(id).orElseThrow().progress(),0);
        assertEquals(saved.toString(),sim.npcFateRuntime(id).orElseThrow());
    }
}
