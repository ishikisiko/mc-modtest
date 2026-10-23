package com.example.myvillage.story;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StoryProgressTest {
    @Test void absentLegacyFieldsMigrateWithoutInventingAcceptance() {
        var p=StoryProgress.CODEC.parse(JsonOps.INSTANCE,new JsonObject()).getOrThrow();assertEquals(1,p.schema());assertFalse(p.accepted());assertTrue(p.evidence().isEmpty());
    }
    @Test void legacySchemaPreservesEvidenceRevisionAndRewardReceipt() {
        var json=JsonParser.parseString("{\"schema\":0,\"stage\":\"final_guide\",\"revision\":17,\"site\":\"qianzhen_test\",\"dimension\":\"minecraft:overworld\",\"evidence\":[\"spring_core\"],\"receipts\":[\"qianzhen_chapter_1_reward\"]}");
        var p=StoryProgress.CODEC.parse(JsonOps.INSTANCE,json).getOrThrow();assertEquals(1,p.schema());assertEquals(17,p.revision());assertEquals(List.of(StoryEngine.RECEIPT),p.receipts());assertTrue(p.evidence().contains("spring_core"));
        var encoded=StoryProgress.CODEC.encodeStart(JsonOps.INSTANCE,p).getOrThrow();assertEquals(p,StoryProgress.CODEC.parse(JsonOps.INSTANCE,encoded).getOrThrow());
    }
    @Test void rejectsFutureSchemaNegativeRevisionAndDuplicateEvidence() {
        for(String s:List.of("{\"schema\":2}","{\"revision\":-1}","{\"evidence\":[\"water_sample\",\"water_sample\"]}","{\"stage\":\"../bad\"}"))assertTrue(StoryProgress.CODEC.parse(JsonOps.INSTANCE,JsonParser.parseString(s)).error().isPresent(),s);
    }
    @Test void valuesAreImmutable() {
        var mutable=new java.util.ArrayList<String>();var p=new StoryProgress(1,StoryProgress.CHAPTER,1,"","","new",0,mutable,List.of(),List.of());mutable.add("water_sample");assertTrue(p.evidence().isEmpty());assertThrows(UnsupportedOperationException.class,()->p.evidence().add("water_sample"));
    }
}
