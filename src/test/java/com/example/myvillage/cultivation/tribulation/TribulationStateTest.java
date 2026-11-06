package com.example.myvillage.cultivation.tribulation;

import static org.junit.jupiter.api.Assertions.*;
import com.example.myvillage.cultivation.*;
import com.example.myvillage.cultivation.data.ModCultivationRegistries;
import com.example.myvillage.cultivation.tribulation.TribulationAttempt.Clock;
import com.example.myvillage.cultivation.tribulation.TribulationAttempt.Phase;
import com.google.gson.JsonParser;
import java.nio.file.*;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.*;

class TribulationStateTest {
    @BeforeAll static void bootstrap(){
        if(LoadingModList.get()==null)LoadingModList.of(List.of(),List.of(),List.of(),List.of(),Map.of());
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
    }
    static TribulationRules rules(TribulationKind kind)throws Exception{
        try(var r=Files.newBufferedReader(Path.of("src/main/resources/data/myvillage/tribulation/"+kind.id()+".json"))){return TribulationRules.read(r);}
    }
    static TribulationAttempt attempt(TribulationKind kind)throws Exception{
        return TribulationAttempt.create(UUID.fromString("11111111-2222-3333-4444-555555555555"),UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"),
                "minecraft:overworld",12,64,-20,kind==TribulationKind.HEAVEN?1900:4200,kind==TribulationKind.HEAVEN?950:2100,90,rules(kind));
    }
    static CultivationProfile profile(TribulationKind kind,int root){
        return CultivationProfile.DEFAULT.withRealmAndStage(ResourceLocation.parse(kind.sourceRealm()),ResourceLocation.parse(kind.sourceStage()))
                .withSpiritualRoot(new SpiritualRoot(Map.of(ModCultivationRegistries.FIRE_ELEMENT_ID,10000)))
                .learnTechnique(ModCultivationRegistries.BASIC_BREATHING_TECHNIQUE_ID).withFoundationQuality(kind==TribulationKind.HEAVEN?0:root)
                .withCultivationProgress(kind==TribulationKind.HEAVEN?1900:4200).withStability(kind==TribulationKind.HEAVEN?951:2101)
                .withCurrentSpiritualPower(17).withLifespanConsumedTicks(456).withMeditationQiReserve(57);
    }
    static TribulationAttempt complete(TribulationAttempt a){
        int budget=a.rules().preparationTicks()+a.rules().durationTicks()+1;
        while(!a.readyToSucceed()&&budget-->0){a=a.advance(true);if(a.due()){int i=a.clock().nextStrike();a=a.consumeStrike().acknowledgeHit(i);}}
        assertTrue(a.readyToSucceed());return a;
    }
    @Test void fourTrialsMatchApprovedCountsAndCurrentRealmTransitions()throws Exception{
        int[] counts={6,6,9,12};
        for(var kind:TribulationKind.values()){
            var r=rules(kind);assertEquals(counts[kind.networkId()],r.requiredHits());assertEquals(kind,r.kind());
            var source=JsonParser.parseString(Files.readString(Path.of("src/main/resources/data/myvillage/myvillage/realm/"+kind.sourceRealm().split(":")[1]+".json"))).getAsJsonObject();
            var stage=source.getAsJsonArray("stages").asList().stream().map(e->e.getAsJsonObject()).filter(e->e.get("id").getAsString().equals(kind.sourceStage())).findFirst().orElseThrow();
            assertEquals(kind.targetStage(),stage.getAsJsonObject("advancement").get("target_stage").getAsString());
            assertTrue(kind.source(profile(kind,1)));assertEquals(kind,TribulationKind.fromNetwork(kind.networkId()));
        }
        assertThrows(IllegalArgumentException.class,()->TribulationKind.fromNetwork(4));
        assertFalse(TribulationKind.canChoose(CultivationProfile.DEFAULT));
    }
    @Test void scheduledOrConsumedStrikesCannotStandInForActualHits()throws Exception{
        var a=attempt(TribulationKind.GOLDEN_HIGH);
        while(!a.due())a=a.advance(true);
        var consumed=a.consumeStrike();assertEquals(0,consumed.acceptedHits());assertTrue(consumed.interruptedHit());assertFalse(consumed.readyToSucceed());
        assertThrows(IllegalStateException.class,()->consumed.acknowledgeHit(1));
        var landed=consumed.acknowledgeHit(0);assertEquals(1,landed.acceptedHits());assertFalse(landed.interruptedHit());
        assertThrows(IllegalStateException.class,()->landed.acknowledgeHit(0));
        var interrupted=TribulationAttempt.decode(consumed.encode());assertTrue(interrupted.interruptedHit());
        for(int i=0;i<interrupted.rules().durationTicks();i++)interrupted=interrupted.advance(true);
        assertEquals(0,interrupted.acceptedHits());assertFalse(interrupted.due());assertFalse(interrupted.readyToSucceed());
    }
    @Test void everyWarningLeadsToOneAcknowledgedHitAndLastHitCompletesImmediately()throws Exception{
        for(var kind:TribulationKind.values()){
            var a=attempt(kind);int warnings=0;
            int budget=a.rules().preparationTicks()+a.rules().durationTicks()+1;
            while(!a.readyToSucceed()&&budget-->0){
                a=a.advance(true);
                for(int i=0;i<a.plan().size();i++)if(a.clock().elapsed()==a.rules().strikes().get(i).tick()-a.rules().strikes().get(i).warningTicks()){assertTrue(a.warning(i));warnings++;}
                if(a.due()){int i=a.clock().nextStrike();a=a.consumeStrike();assertFalse(a.readyToSucceed());a=a.acknowledgeHit(i);}
            }
            assertTrue(a.readyToSucceed());assertEquals(a.rules().requiredHits(),a.acceptedHits());assertEquals(a.acceptedHits(),warnings);
            assertEquals(a.rules().strikes().getLast().tick(),a.clock().elapsed());
        }
    }
    @Test void restartKeepsKindHitsFrozenRulesAndRemainingTimeline()throws Exception{
        var a=attempt(TribulationKind.GOLDEN_HIGH);
        while(a.acceptedHits()<2){a=a.advance(true);if(a.due()){int i=a.clock().nextStrike();a=a.consumeStrike().acknowledgeHit(i);}}
        var before=a;a=TribulationAttempt.decode(a.pause().encode());
        assertEquals(before.id(),a.id());assertEquals(before.rules(),a.rules());assertEquals(before.plan(),a.plan());assertEquals(2,a.acceptedHits());
        for(int i=0;i<a.rules().resumeTicks();i++){a=a.advance(true);assertEquals(before.clock().elapsed(),a.clock().elapsed());assertFalse(a.due());}
        a=a.advance(true);assertEquals(before.clock().elapsed()+1,a.clock().elapsed());assertEquals(2,a.clock().nextStrike());
        var status=TribulationManager.statusJson(a);assertEquals(2,status.get("schema").getAsInt());assertEquals(12,status.get("required_hits").getAsInt());
        assertEquals(TribulationManager.statusJson(before).get("rules_sha256"),status.get("rules_sha256"));
    }
    @Test void everyOriginCanPursueEveryGoldenGradeWithoutRepeatedRootRewards()throws Exception{
        for(var kind:TribulationKind.values())for(int root=1;root<=4;root++){
            var a=complete(attempt(kind));var old=profile(kind,root);var next=TribulationSettlement.success(a,old);
            assertEquals(kind.targetStage(),next.stageId().toString());assertEquals(0,next.cultivationProgress());assertEquals(old.stability()/2,next.stability());
            assertEquals(kind==TribulationKind.HEAVEN?4:root,next.foundationQuality());assertEquals(kind==TribulationKind.HEAVEN?0:kind.quality(),next.goldenCoreQuality());
            assertEquals(old.currentSpiritualPower(),next.currentSpiritualPower());assertEquals(old.lifespanConsumedTicks(),next.lifespanConsumedTicks());assertEquals(old.learnedTechniques(),next.learnedTechniques());
            assertThrows(IllegalStateException.class,()->TribulationSettlement.success(a,next));
            assertThrows(IllegalStateException.class,()->TribulationSettlement.success(a,old.withStability(0)));
            var c=a.clock();var ended=a.withClock(new Clock(Phase.SUCCEEDED,0,c.elapsed(),c.nextStrike(),0,0,false,"成功",false));
            assertFalse(TribulationAttempt.decode(ended.encode()).readyToSucceed());assertThrows(IllegalStateException.class,()->TribulationSettlement.success(ended,old));
        }
    }
    @Test void failedTrialLeavesNoQualityAndPunishesOnceWithoutHealing()throws Exception{
        assertEquals(18,TribulationDamage.nonLethal(10,10,2));assertEquals(0,TribulationDamage.nonLethal(1,0,2));
        for(var kind:TribulationKind.values()){
            var a=attempt(kind);var old=profile(kind,1);var first=TribulationSettlement.failure(a,old,"承受不住",true);
            assertEquals(old.stability()-a.rules().failureStabilityLoss(a.requiredStability()),first.profile().stability());
            assertEquals(old.stageId(),first.profile().stageId());assertEquals(old.cultivationProgress(),first.profile().cultivationProgress());
            assertEquals(old.foundationQuality(),first.profile().foundationQuality());assertEquals(old.goldenCoreQuality(),first.profile().goldenCoreQuality());
            assertEquals(first,TribulationSettlement.failure(TribulationAttempt.decode(first.attempt().encode()),first.profile(),"重放",true));
            var stopped=TribulationSettlement.failure(a,old,"管理终止",false);assertEquals(old,stopped.profile());assertFalse(stopped.attempt().clock().penaltyApplied());
            assertThrows(IllegalStateException.class,()->TribulationSettlement.success(stopped.attempt(),old));
        }
    }
    @Test void boundariesOwnerAndMalformedPersistenceRemainStrict()throws Exception{
        var a=attempt(TribulationKind.HEAVEN);assertTrue(a.contains(a.x()+a.rules().arenaRadius(),a.y(),a.z()));assertFalse(a.contains(a.x()+a.rules().arenaRadius()+.01,a.y(),a.z()));
        assertFalse(a.canHit(UUID.randomUUID(),0,a.x(),a.y(),a.z()));assertTrue(a.canHit(a.owner(),0,a.x(),a.y(),a.z()));
        assertFalse(a.canHit(a.owner(),32,a.x(),a.y(),a.z()));assertEquals(a,TribulationAttempt.decode(a.encode()));
        for(String field:List.of("accepted_hits","schema_version")){
            var json=JsonParser.parseString(a.encode()).getAsJsonObject();json.addProperty(field,99);assertThrows(RuntimeException.class,()->TribulationAttempt.decode(json.toString()));
        }
        var json=JsonParser.parseString(a.encode()).getAsJsonObject();json.getAsJsonObject("rules").addProperty("required_hits",9);assertThrows(RuntimeException.class,()->TribulationAttempt.decode(json.toString()));
    }
}
