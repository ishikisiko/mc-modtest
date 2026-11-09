package com.example.myvillage.sim;

import com.example.myvillage.sim.engine.*;
import com.example.myvillage.sim.model.*;
import com.example.myvillage.sim.combat.NpcCombatProfile;
import com.example.myvillage.stats.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NpcCultivationTest {
    private SimContext context() {
        var s = new WorldState(); s.seed = 21; s.tierId = "small"; s.genesisDaysPerYear = 6;
        return new SimContext(s, SimFixtures.data(), SimFixtures.graph(21), 6);
    }
    private Person person(SimContext ctx, int id, int realm) {
        var p = new Person(); p.id = id; p.given = "测试" + id; p.realm = realm;
        p.root = new int[]{10000,0,0,0,0};
        p.regionId = ctx.graph.regions().getFirst().id(); p.homeRegionId = p.regionId;
        NpcCultivation.initialize(ctx,p); ctx.state.persons.put(id,p); ctx.state.nextPersonId = id + 1;
        return p;
    }
    @Test void seededBirthDistributionAndIndependentQualityDraws() {
        var ctx = context(); int prodigies=0, middleF=0, highF=0, middleG=0, highG=0, different=0;
        for(int id=1;id<=20_000;id++) {
            Person p = person(ctx,id,2);
            if(p.prodigy) {
                prodigies++;
                if(p.foundationQuality==2) middleF++; else { assertEquals(3,p.foundationQuality); highF++; }
                if(p.goldenCoreQuality==2) middleG++; else { assertEquals(3,p.goldenCoreQuality); highG++; }
                if(p.foundationQuality!=p.goldenCoreQuality) different++;
            } else { assertEquals(1,p.foundationQuality); assertEquals(1,p.goldenCoreQuality); }
        }
        assertTrue(prodigies>1800 && prodigies<2200,"prodigies="+prodigies);
        for(int count:new int[]{middleF,highF,middleG,highG,different})
            assertTrue(count>prodigies*.44 && count<prodigies*.56,"count="+count+" of "+prodigies);
    }
    @Test void qualitiesAreDeferredUntilReachingTheRealmAndNeverRerolled() {
        var ctx=context(); var p=person(ctx,1,0); p.prodigy=true;
        assertEquals(0,p.foundationQuality); assertEquals(0,p.goldenCoreQuality);
        long qi=p.spiritualPower; p.realm=1; NpcCultivation.reached(ctx,p);
        int fq=p.foundationQuality; assertTrue(fq==2||fq==3); assertEquals(0,p.goldenCoreQuality);
        assertEquals(qi,p.spiritualPower,"突破只增加容量，不补满");
        p.realm=2; NpcCultivation.reached(ctx,p); int gq=p.goldenCoreQuality;
        for(int i=0;i<20;i++) { ctx.state.day++; p.stage=i%3; NpcCultivation.reached(ctx,p);
            assertEquals(fq,p.foundationQuality); assertEquals(gq,p.goldenCoreQuality); }
    }
    @Test void allNascentOriginsRetainEarlierDifferencesAndLinearMinorGrowth() {
        var r=CultivationStatsRules.bundled();
        assertEquals(new CultivationStatsRules.Body(184,52,16,640),r.at(new RealmKey("nascent_soul",0),1,1));
        for(int fq=1;fq<=3;fq++)for(int gq=1;gq<=3;gq++)for(int stage=0;stage<3;stage++) {
            var before=r.at(new RealmKey("golden_core",2),fq,gq);
            var after=r.at(new RealmKey("nascent_soul",stage),fq,gq);
            assertEquals(60+20*stage,after.health()-before.health());
            assertEquals(24+4*stage,after.attack()-before.attack());
            assertEquals(6+2*stage,after.defense()-before.defense());
            assertEquals(240+80*stage,after.power()-before.power());
            var nativeStats=RealmStats.derived(StatsRules.bundled(),new RealmKey("nascent_soul",stage),20,1,fq,gq);
            assertEquals(after.health(),nativeStats.health()); assertEquals(after.attack(),nativeStats.attack());
            assertEquals(after.defense(),nativeStats.defense()); assertEquals(1,nativeStats.factor());
        }
    }
    @Test void spendRefusalIsAtomicAndRecoveryClampsWithoutOverflow() {
        var ctx=context();var p=person(ctx,1,0);long cap=p.spiritualPower;
        assertFalse(NpcCultivation.spend(ctx,1,cap+1)); assertEquals(cap,p.spiritualPower);
        assertTrue(NpcCultivation.spend(ctx,1,cap));assertEquals(0,p.spiritualPower);
        assertFalse(NpcCultivation.spend(ctx,1,1));assertEquals(0,p.spiritualPower);
        assertTrue(NpcCultivation.recover(ctx,1,Long.MAX_VALUE));assertEquals(cap,p.spiritualPower);
        assertFalse(NpcCultivation.recover(ctx,1,1));
        assertThrows(IllegalArgumentException.class,()->NpcCultivation.spend(ctx,1,-1));
        assertThrows(IllegalArgumentException.class,()->NpcCultivation.recover(ctx,1,-1));
        assertFalse(NpcCultivation.spend(ctx,999,1));assertFalse(NpcCultivation.recover(ctx,999,1));
    }
    @Test void promotionPreservesAbsoluteHealthAndRepeatedRefreshIsIdempotent() {
        var ctx=context();var p=person(ctx,1,0);
        p.fates=new com.example.myvillage.sim.fate.NpcFateRoll(java.util.List.of("myvillage:bright","myvillage:herbal_lore","myvillage:lightfoot"),9,3);
        ctx.state.combat.profiles.put(1,new NpcCombatProfile(7,40,"saved sword",3,4,"npc:2",5,null));
        var previous=NpcCultivation.body(ctx,p);p.realm=1;NpcCultivation.advanced(ctx,p,previous);
        var checkpoint=ctx.state.combat.profiles.get(1);
        assertEquals(7,checkpoint.health());assertEquals(NpcCultivation.body(ctx,p).health()+10,checkpoint.maximumHealth());
        assertEquals("saved sword",checkpoint.weapon());assertEquals(5,checkpoint.revision());
        NpcCultivation.reached(ctx,p);assertEquals(checkpoint,ctx.state.combat.profiles.get(1));
    }
    @Test void promotionAddsTheEarnedFateHealthDifferenceWithoutHealing() {
        var ctx=context();var p=person(ctx,1,0);
        p.fates=new com.example.myvillage.sim.fate.NpcFateRoll(java.util.List.of("myvillage:robust","myvillage:bright","myvillage:herbal_lore"),9,3);
        ctx.state.combat.profiles.put(1,new NpcCombatProfile(7,40,"saved sword",3,4,"npc:2",5,null));
        var previous=NpcCultivation.body(ctx,p);p.realm=1;NpcCultivation.advanced(ctx,p,previous);
        var saved=ctx.state.combat.profiles.get(1);
        assertEquals(7,saved.health());
        assertEquals(40+(NpcCultivation.body(ctx,p).health()-previous.health())*1.15,saved.maximumHealth(),1e-9);
        assertEquals("saved sword",saved.weapon());
    }
    @Test void roundTripRetainsIdentityBothQualitiesAndDepletedPowerWithoutBirthDraw() {
        var ctx=context();for(int id=1;id<100;id++) { var p=person(ctx,id,id%4); NpcCultivation.spend(ctx,id,7); }
        byte[] before=StateCodec.toBytes(ctx.state,ctx.realms);
        var saved=StateCodec.fromBytes(before,ctx.realms);
        assertArrayEquals(before,StateCodec.toBytes(saved,ctx.realms));
        for(var p:ctx.state.persons.values()) {
            var q=saved.persons.get(p.id);assertEquals(p.prodigy,q.prodigy);
            assertEquals(p.foundationQuality,q.foundationQuality);assertEquals(p.goldenCoreQuality,q.goldenCoreQuality);
            assertEquals(p.spiritualPower,q.spiritualPower);
        }
    }
    @Test void currentSchemaRejectsMissingIdentityWrongBooleanInvalidQualityAndOverCapPower() {
        var ctx=context();person(ctx,1,2);
        for(String key:new String[]{"prodigy","foundation_quality","golden_core_quality","spiritual_power"}) {
            var root=StateCodec.toJson(ctx.state,ctx.realms);root.getAsJsonArray("persons").get(0).getAsJsonObject().remove(key);
            assertThrows(SimFormatException.class,()->StateCodec.fromBytes(root.toString().getBytes(StandardCharsets.UTF_8),ctx.realms));
        }
        for(String change:new String[]{"boolean","quality","negative","overcap"}) {
            var root=StateCodec.toJson(ctx.state,ctx.realms);var p=root.getAsJsonArray("persons").get(0).getAsJsonObject();
            switch(change) {
                case "boolean" -> p.addProperty("prodigy","true");
                case "quality" -> p.addProperty("foundation_quality",4);
                case "negative" -> p.addProperty("spiritual_power",-1);
                case "overcap" -> p.addProperty("spiritual_power",Long.MAX_VALUE);
            }
            assertThrows(SimFormatException.class,()->StateCodec.fromBytes(root.toString().getBytes(StandardCharsets.UTF_8),ctx.realms));
        }
    }
    @Test void projectionLeaseAndDailyOfflineSettlementNeverRefillQi() {
        var sim=SimFixtures.genesis(21,"small",6);var p=sim.findPersons("",10).getFirst();
        assertTrue(sim.trySpendSpiritualPower(p.id(),p.spiritualPower()));
        long epoch=sim.claimCombat(p.id(),new NpcCombatProfile(7,30,"",0,0,"",0,null));
        assertTrue(epoch>0);assertEquals(0,sim.person(p.id()).orElseThrow().spiritualPower());
        assertTrue(sim.releaseCombat(p.id(),epoch));
        assertTrue(sim.claimCombat(p.id(),new NpcCombatProfile(30,30,"",0,0,"",0,null))>epoch);
        assertEquals(0,sim.person(p.id()).orElseThrow().spiritualPower());
        var saved=WorldSim.fromBytes(sim.toBytes(),SimFixtures.graph(21),SimFixtures.data());
        assertEquals(0,saved.person(p.id()).orElseThrow().spiritualPower());
        SimFixtures.run(saved,5,6);assertEquals(0,saved.person(p.id()).orElseThrow().spiritualPower());
    }
    @Test void independentAptitudesSurviveLedgerRoundTripWithoutReroll() {
        var ctx=context(); var p=person(ctx,1,2);
        NpcAptitudes.initialize(ctx,p);
        p.aptitudes=p.aptitudes.with("sword",350).with("alchemy",500);
        p.root=new int[]{300,200,40,75,10};
        var restored=StateCodec.fromBytes(StateCodec.toBytes(ctx.state,ctx.realms),ctx.realms).persons.get(p.id);
        assertEquals(p.aptitudes,restored.aptitudes);
        assertArrayEquals(p.root,restored.root);
        assertEquals(350,restored.aptitudes.get("sword"));
        assertEquals(500,restored.aptitudes.get("alchemy"));
    }
}
