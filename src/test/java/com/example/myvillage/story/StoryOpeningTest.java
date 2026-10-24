package com.example.myvillage.story;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StoryOpeningTest {
    private StoryDefinition opening() throws IOException {
        try(var reader=Files.newBufferedReader(Path.of("src/main/resources/data/myvillage/story/qianzhen_opening.json"))) {
            return StoryDefinition.parse(reader);
        }
    }
    private StoryProgress apply(StoryDefinition d,StoryProgress p,String id) {
        var a=d.actions().get(id);
        assertNotEquals("fact",a.kind(),"server facts are not player actions");
        var plan=StoryEngine.reduce(d,p,id,a.kind(),a.role(),"qianzhen_test","minecraft:overworld");
        assertTrue(plan.changed(),id+" at "+p.stage());return plan.progress();
    }
    private StoryProgress at(String stage,List<String> receipts) {
        return new StoryProgress(1,StoryProgress.CHAPTER,2,"qianzhen_test","minecraft:overworld",stage,8,List.of(),List.of(),receipts);
    }
    @Test void newPlayerMustAcceptAtRealEmployerAndDoesNotStartLegacyInvestigation() throws Exception {
        var d=opening();assertEquals(2,d.version());assertEquals("药庐学徒",d.title());
        assertFalse(StoryProgress.EMPTY.accepted());
        assertFalse(StoryEngine.reduce(d,StoryProgress.EMPTY,"accept","talk","guide","qianzhen_test","minecraft:overworld").changed());
        var p=apply(d,StoryProgress.EMPTY,"accept");assertEquals("testing",p.stage());assertEquals(2,p.definitionVersion());
        assertTrue(p.receipts().isEmpty());assertTrue(p.evidence().isEmpty());
        assertTrue(d.actions().values().stream().noneMatch(a->a.kind().equals("investigate")));
    }
    @Test void savedLegacyAndCompletedLegacyKeepExactDefinitionAndState() throws Exception {
        var old=StoryTestData.definition();var d=opening();var p=StoryTestData.run("accept","commission","inspect_water_sample");
        assertSame(old,StoryOpening.definition(p,old,d));assertEquals("samples",p.stage());assertEquals(List.of("water_sample"),p.evidence());
        assertSame(d,StoryOpening.definition(StoryProgress.EMPTY,old,d));
        assertSame(old,StoryOpening.definition(new StoryProgress(1,p.chapter(),1,p.site(),p.dimension(),"complete",30,p.evidence(),p.journal(),List.of(StoryEngine.RECEIPT)),old,d));
        assertSame(p,StoryOpening.recordBreath(p));assertSame(p,StoryOpening.recordPlant(p,7,3));
    }
    @Test void currentRootFateAndTechniqueMustBeVerifiedWithoutChangingTheirData() throws Exception {
        var d=opening();var p=apply(d,StoryProgress.EMPTY,"accept");
        assertFalse(StoryOpening.allowed("verify_root",p,false,true,false,3));
        assertFalse(StoryOpening.allowed("verify_root",p,true,false,false,3));
        assertTrue(StoryOpening.allowed("verify_root",p,true,true,false,3));
        assertFalse(StoryOpening.allowed("verify_inheritance",p,true,true,false,3));
        assertTrue(StoryOpening.allowed("verify_inheritance",p,true,true,true,3));
        assertSame(p,StoryOpening.recordBreath(p));
    }
    @Test void firstBreathRequiresSettlementReceiptWhileExperiencedPlayerCanKeepPriorPractice() {
        var p=at("first_breath",List.of());
        assertFalse(StoryOpening.allowed("verify_breath",p,true,true,true,3));
        var settled=StoryOpening.recordBreath(p);
        assertTrue(StoryOpening.allowed("verify_breath",settled,true,true,true,3));
        assertSame(settled,StoryOpening.recordBreath(settled));
        var experienced=StoryOpening.receipt(p,StoryOpening.EXPERIENCED);
        assertTrue(StoryOpening.allowed("verify_breath",experienced,true,true,true,3));
        assertFalse(experienced.receipts().contains(StoryOpening.BREATH));
    }
    @Test void plantingCountsDistinctVerifiedOwnPositionsOnlyAfterSuppliesAndDoesNotAffectOtherPlayers() {
        var noSupplies=at("planting",List.of());assertSame(noSupplies,StoryOpening.recordPlant(noSupplies,5,3));
        var p=StoryOpening.receipt(noSupplies,StoryOpening.SUPPLIES);var other=p;
        p=StoryOpening.recordPlant(p,5,3);assertEquals(1,StoryOpening.planted(p));
        assertSame(p,StoryOpening.recordPlant(p,5,3));assertEquals(0,StoryOpening.planted(other));
        p=StoryOpening.recordPlant(p,Long.MIN_VALUE,3);assertEquals(2,StoryOpening.planted(p));
        assertFalse(StoryOpening.allowed("verify_work",p,true,true,true,3));
        p=StoryOpening.recordPlant(p,Long.MAX_VALUE,3);assertEquals(3,StoryOpening.planted(p));
        assertTrue(StoryOpening.allowed("verify_work",p,true,true,true,3));
        assertSame(p,StoryOpening.recordPlant(p,77,3));assertFalse(StoryOpening.allowed("supply_work",p,true,true,true,3));
    }
    @Test void laterPlayerCanReuseSameMatureHarvestedBedsWithoutSharingProgressOrRewards() {
        var first=at("planting",List.of(StoryOpening.SUPPLIES));
        var later=at("planting",List.of(StoryOpening.SUPPLIES));
        for(long pos:new long[]{7,8,9})first=StoryOpening.recordPlant(first,pos,3);
        assertEquals(0,StoryOpening.planted(later));
        // The runtime independently verifies each later player's actual new planting after vanilla harvest.
        for(long pos:new long[]{7,8,9})later=StoryOpening.recordPlant(later,pos,3);
        assertEquals(3,StoryOpening.planted(first));assertEquals(3,StoryOpening.planted(later));
        assertFalse(first.receipts().contains(StoryEngine.RECEIPT));assertFalse(later.receipts().contains(StoryEngine.RECEIPT));
    }
    @Test void practiceAndDistinctBedReceiptsSurviveCodecRoundTripAndContinueWithoutReplayingSupplies() {
        var p=at("planting",List.of(StoryOpening.BREATH,StoryOpening.SUPPLIES));
        p=StoryOpening.recordPlant(p,Long.MIN_VALUE,3);
        var saved=StoryProgress.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE,p).getOrThrow();
        var loaded=StoryProgress.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,saved).getOrThrow();
        assertEquals(p,loaded);assertSame(loaded,StoryOpening.recordPlant(loaded,Long.MIN_VALUE,3));
        assertTrue(StoryOpening.breathingDone(loaded));assertFalse(StoryOpening.allowed("supply_work",loaded,true,true,true,3));
        assertEquals(2,StoryOpening.planted(StoryOpening.recordPlant(loaded,Long.MAX_VALUE,3)));
    }
    @Test void completeOpeningNeedsPracticeAndWorkBeforeFiniteOnceOnlyPay() throws Exception {
        var d=opening();var p=StoryProgress.EMPTY;
        p=apply(d,p,"accept");p=StoryOpening.reconcile(d,p,new StoryOpening.Facts(true,true,true));
        assertEquals("first_breath",p.stage());
        assertFalse(StoryOpening.breathingDone(p));p=StoryOpening.recordBreath(p);
        p=StoryOpening.reconcile(d,p,new StoryOpening.Facts(true,true,true));
        p=apply(d,p,"take_supplies");p=StoryOpening.receipt(p,StoryOpening.SUPPLIES);
        for(long pos:new long[]{1,2,3})p=StoryOpening.recordPlant(p,pos,d.work().count());
        p=StoryOpening.reconcile(d,p,new StoryOpening.Facts(true,true,true));assertEquals("report",p.stage());
        assertFalse(p.receipts().contains(StoryEngine.RECEIPT));p=apply(d,p,"claim_reward");
        assertTrue(p.completed());assertTrue(p.receipts().contains(StoryEngine.RECEIPT));
        assertFalse(StoryTestData.act(d,p,"claim_reward").changed());
        assertEquals(List.of(new StoryDefinition.Reward("minecraft:bread",6),new StoryDefinition.Reward("myvillage:low_grade_spirit_stone",1)),d.rewards());
    }
    @Test void workConfigurationIsBoundedAndCannotGrantArbitraryUnboundPlanting() throws Exception {
        var path=Path.of("src/main/resources/data/myvillage/story/qianzhen_opening.json");
        for(String field:List.of("count","radius")) {
            var json=JsonParser.parseString(Files.readString(path)).getAsJsonObject();json.getAsJsonObject("work").addProperty(field,0);
            assertThrows(RuntimeException.class,()->StoryDefinition.parse(new StringReader(json.toString())));
        }
        var missing=JsonParser.parseString(Files.readString(path)).getAsJsonObject();missing.remove("work");
        assertThrows(RuntimeException.class,()->StoryDefinition.parse(new StringReader(missing.toString())));
    }
    @Test void teachingFactsAdvanceWithoutReturnTripsAndMarkerTracksTheNextRealAction() throws Exception {
        var d=opening();var p=apply(d,StoryProgress.EMPTY,"accept");
        assertSame(p,StoryOpening.reconcile(d,p,new StoryOpening.Facts(false,true,true)));
        assertSame(p,StoryOpening.reconcile(d,p,new StoryOpening.Facts(true,false,true)));
        p=StoryOpening.reconcile(d,p,new StoryOpening.Facts(true,true,false));
        assertEquals("inheritance",p.stage());assertEquals("inheritance_stele",d.tracking(p,false,false).target());
        assertSame(p,StoryOpening.reconcile(d,p,new StoryOpening.Facts(true,true,false)));
        p=StoryOpening.reconcile(d,p,new StoryOpening.Facts(true,true,true));
        assertEquals("first_breath",p.stage());assertEquals("",d.tracking(p,false,false).target());
        assertTrue(p.receipts().isEmpty());assertTrue(p.evidence().isEmpty());
        p=StoryOpening.recordBreath(p);p=StoryOpening.reconcile(d,p,new StoryOpening.Facts(true,true,true));
        assertEquals("work_offer",p.stage());assertEquals("pharmacist",d.tracking(p,false,false).target());
        assertEquals(List.of(StoryOpening.BREATH),p.receipts());
        assertFalse(p.receipts().contains(StoryOpening.SUPPLIES));assertFalse(p.receipts().contains(StoryEngine.RECEIPT));
    }
    @Test void factsNeverAcceptOrAlterLegacyOrCompletedChaptersAndCannotBeSentAsTalk() throws Exception {
        var d=opening();var facts=new StoryOpening.Facts(true,true,true);
        assertSame(StoryProgress.EMPTY,StoryOpening.reconcile(d,StoryProgress.EMPTY,facts));
        var legacy=StoryTestData.run("accept","commission");assertSame(legacy,StoryOpening.reconcile(d,legacy,facts));
        var complete=at("complete",List.of(StoryEngine.RECEIPT));assertSame(complete,StoryOpening.reconcile(d,complete,facts));
        for(String id:List.of("confirm_root","confirm_inheritance","confirm_breath","report_work")) {
            var action=d.actions().get(id);assertEquals("fact",action.kind());
            assertFalse(StoryEngine.reduce(d,at(action.stages().getFirst(),List.of()),id,"talk","pharmacist","qianzhen_test","minecraft:overworld").changed());
        }
    }
    @Test void experiencedAcceptanceReconcilesInOneBoundedPassWithoutSuppliesOrPay() throws Exception {
        assertFalse(StoryOpening.experienced(true,false,0,0));
        assertFalse(StoryOpening.experienced(false,true,10,10));
        assertTrue(StoryOpening.experienced(true,true,0,0));
        assertTrue(StoryOpening.experienced(true,false,1,0));
        assertTrue(StoryOpening.experienced(true,false,0,1));
        var d=opening();var accepted=StoryOpening.receipt(apply(d,StoryProgress.EMPTY,"accept"),StoryOpening.EXPERIENCED);
        var p=StoryOpening.reconcile(d,accepted,new StoryOpening.Facts(true,true,true));
        assertEquals("work_offer",p.stage());assertEquals(accepted.revision()+3,p.revision());
        assertEquals(accepted.site(),p.site());assertEquals(accepted.dimension(),p.dimension());
        assertEquals(accepted.receipts(),p.receipts());assertEquals(accepted.evidence(),p.evidence());
        assertSame(p,StoryOpening.reconcile(d,p,new StoryOpening.Facts(true,true,true)));
    }
    @Test void restartAndRepeatedTicksPreserveAutomaticProgressAndPersonalWork() throws Exception {
        var d=opening();var facts=new StoryOpening.Facts(true,true,true);
        var p=StoryOpening.recordBreath(StoryOpening.reconcile(d,apply(d,StoryProgress.EMPTY,"accept"),facts));
        var json=StoryProgress.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE,p).getOrThrow();
        p=StoryProgress.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,json).getOrThrow();
        p=StoryOpening.reconcile(d,p,facts);assertEquals("work_offer",p.stage());
        for(int i=0;i<100;i++)assertSame(p,StoryOpening.reconcile(d,p,facts));
        p=StoryOpening.receipt(apply(d,p,"take_supplies"),StoryOpening.SUPPLIES);var other=p;
        for(long pos:new long[]{1,2,3})p=StoryOpening.recordPlant(p,pos,d.work().count());
        json=StoryProgress.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE,p).getOrThrow();
        p=StoryProgress.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,json).getOrThrow();
        p=StoryOpening.reconcile(d,p,facts);assertEquals("report",p.stage());
        assertSame(p,StoryOpening.reconcile(d,p,facts));assertEquals("planting",other.stage());assertEquals(0,StoryOpening.planted(other));
        assertEquals("pharmacist",d.tracking(p,false,false).target());assertFalse(p.receipts().contains(StoryEngine.RECEIPT));
    }
    @Test void serverFactDefinitionCannotBeChangedIntoARewardTalkOrCycle() throws Exception {
        for(String change:List.of("talk","reward","cycle","duplicate")) {
            var json=JsonParser.parseString(Files.readString(Path.of("src/main/resources/data/myvillage/story/qianzhen_opening.json"))).getAsJsonObject();
            var actions=json.getAsJsonArray("actions");
            var fact=java.util.stream.StreamSupport.stream(actions.spliterator(),false).map(e->e.getAsJsonObject())
                    .filter(a->a.get("id").getAsString().equals("confirm_root")).findFirst().orElseThrow();
            switch(change) {
                case "talk" -> fact.addProperty("kind","talk");
                case "reward" -> fact.addProperty("effect","supply_work");
                case "cycle" -> fact.addProperty("next","testing");
                case "duplicate" -> {var clone=fact.deepCopy();clone.addProperty("id","extra_fact");actions.add(clone);}
            }
            assertThrows(RuntimeException.class,()->StoryDefinition.parse(new StringReader(json.toString())),change);
        }
    }
    @Test void sameTickReplacementCannotBorrowTheOriginalPlayersPlantWitness() {
        var original=new java.util.concurrent.atomic.AtomicBoolean(false);
        var broken=new java.util.concurrent.atomic.AtomicBoolean(false);
        var replacement=new java.util.concurrent.atomic.AtomicBoolean(false);
        var first=new StoryOpening.PlantWitness<>(original);assertTrue(first.intact(java.util.concurrent.atomic.AtomicBoolean::get));
        first.changedAfter(broken);first.changedAfter(replacement);
        var second=new StoryOpening.PlantWitness<>(replacement);
        assertFalse(first.intact(java.util.concurrent.atomic.AtomicBoolean::get));assertTrue(second.intact(java.util.concurrent.atomic.AtomicBoolean::get));
        // A canceled later break/placement rolls back, so it does not steal the successful original event.
        broken.set(true);replacement.set(true);assertTrue(first.intact(java.util.concurrent.atomic.AtomicBoolean::get));
        assertFalse(second.intact(java.util.concurrent.atomic.AtomicBoolean::get));
        original.set(true);assertFalse(first.intact(java.util.concurrent.atomic.AtomicBoolean::get));
    }
}
