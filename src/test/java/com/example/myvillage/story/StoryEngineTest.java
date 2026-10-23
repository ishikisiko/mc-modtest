package com.example.myvillage.story;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StoryEngineTest {
    private static final String[] MEDICAL={"accept","commission","inspect_water_sample","inspect_withered_crop","choose_medical","examine_sample","read_record","return_zhou_he","hear_warden","inspect_recut_array","inspect_fresh_cut","inspect_old_array_text","regulate_spring","claim_reward","recognize_contract"};
    private static final String[] ROAD={"accept","commission","inspect_water_sample","inspect_withered_crop","choose_road","read_record","inspect_chest","return_zhou_he","hear_warden","inspect_old_array_text","inspect_fresh_cut","inspect_recut_array","regulate_spring","claim_reward","recognize_contract"};
    @Test void bothRealInvestigationRoutesReachEndingWithoutAwakeningOrAlchemyProduction() {
        for(var ids:new String[][]{MEDICAL,ROAD}) {var p=StoryTestData.run(ids);assertTrue(p.completed());assertEquals(ids.length,p.revision());assertEquals(java.util.List.of(StoryEngine.RECEIPT),p.receipts());assertTrue(p.evidence().contains("transport_record"));}
    }
    @Test void illegalStagesAndWrongActorsNeverAdvance() {
        var d=StoryTestData.definition();
        for(String id:new String[]{"commission","examine_sample","return_zhou_he","regulate_spring","claim_reward","recognize_contract"})assertFalse(StoryTestData.act(d,StoryProgress.EMPTY,id).changed(),id);
        assertFalse(StoryEngine.reduce(d,StoryProgress.EMPTY,"accept","talk","pharmacist","site","minecraft:overworld").changed());
        assertFalse(StoryEngine.reduce(d,StoryProgress.EMPTY,"accept","investigate","guide","site","minecraft:overworld").changed());
    }
    @Test void duplicateEvidenceDoesNotChangeRevisionOrJournal() {
        var d=StoryTestData.definition();var p=StoryTestData.run("accept","commission","inspect_water_sample");
        var again=StoryTestData.act(d,p,"inspect_water_sample");assertFalse(again.changed());assertSame(p,again.progress());
        assertEquals("samples",p.stage());
    }
    @Test void roadCannotSkipChestEvenWhenRecordIsReadFirst() {
        var p=StoryTestData.run("accept","commission","inspect_water_sample","inspect_withered_crop","choose_road","read_record");
        assertEquals("road",p.stage());assertFalse(StoryTestData.act(StoryTestData.definition(),p,"return_zhou_he").changed());
    }
    @Test void medicalStillRequiresActualTransportRecordBeforeRescue() {
        var p=StoryTestData.run("accept","commission","inspect_water_sample","inspect_withered_crop","choose_medical","examine_sample");
        assertEquals("zhou",p.stage());assertFalse(StoryTestData.act(StoryTestData.definition(),p,"return_zhou_he").changed());
    }
    @Test void independentPlayersDoNotInheritSharedRescueOrRegulation() {
        var a=StoryTestData.run(MEDICAL);var b=StoryProgress.EMPTY;
        assertTrue(a.completed());assertFalse(b.accepted());assertTrue(b.journal().isEmpty());
        var justAccepted=StoryTestData.run("accept");assertFalse(StoryTestData.act(StoryTestData.definition(),justAccepted,"claim_reward").changed());
    }
    @Test void rewardIsOneReceiptAndReplaysAreRefused() {
        var p=StoryTestData.run(java.util.Arrays.copyOf(MEDICAL,14));var d=StoryTestData.definition();
        assertEquals("final_guide",p.stage());assertFalse(StoryTestData.act(d,p,"claim_reward").changed());
        var forgedPending=new StoryProgress(1,p.chapter(),1,p.site(),p.dimension(),"report",p.revision(),p.evidence(),p.journal(),p.receipts());
        assertFalse(StoryTestData.act(d,forgedPending,"claim_reward").changed());
    }
    @Test void incompatibleDefinitionVersionFailsClosedWithoutLosingReceipts() {
        var d=StoryTestData.definition();var p=new StoryProgress(1,StoryProgress.CHAPTER,2,"qianzhen_test","minecraft:overworld","report",17,java.util.List.of(),java.util.List.of(),java.util.List.of(StoryEngine.RECEIPT));
        var plan=StoryTestData.act(d,p,"claim_reward");assertFalse(plan.changed());assertSame(p,plan.progress());assertEquals(java.util.List.of(StoryEngine.RECEIPT),plan.progress().receipts());
    }
    @Test void siteAndDimensionStayBoundAfterAcceptance() {
        var d=StoryTestData.definition();var p=StoryTestData.run("accept");
        assertFalse(StoryEngine.reduce(d,p,"commission","talk","pharmacist","other_site",p.dimension()).changed());
        assertFalse(StoryEngine.reduce(d,p,"commission","talk","pharmacist",p.site(),"minecraft:the_nether").changed());
    }
}
