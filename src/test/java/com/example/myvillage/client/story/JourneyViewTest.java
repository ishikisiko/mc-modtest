package com.example.myvillage.client.story;

import com.example.myvillage.story.net.StorySnapshotPayload;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JourneyViewTest {
    private static final UUID OWNER=UUID.fromString("12345678-1234-1234-1234-123456789abc");
    private static final String REALM="myvillage:qianzhen/"+OWNER;
    private static StorySnapshotPayload snapshot(boolean accepted,boolean completed,String dimension,List<String> journal) {
        return new StorySnapshotPayload(1,true,accepted,completed,"qianzhen_chapter_1","药庐学徒","当前目标","提示",
                dimension,BlockPos.ZERO,journal);
    }
    @Test void exitDoesNotRequireAcceptanceCompletionOrCultivationSnapshot() {
        for(var p:List.of(snapshot(false,false,REALM,List.of()),snapshot(true,false,REALM,List.of()),snapshot(true,true,"",List.of())))
            assertEquals(JourneyView.Travel.LEAVE,JourneyView.of(p,OWNER,REALM).travel());
        assertEquals(JourneyView.Travel.LEAVE,JourneyView.of(null,OWNER,REALM).travel());
    }
    @Test void outsideRealmOffersEntryAndLegacyTasksKeepTheirOldBoundary() {
        assertEquals(JourneyView.Travel.ENTER,JourneyView.of(snapshot(true,false,REALM,List.of()),OWNER,"minecraft:overworld").travel());
        assertEquals(JourneyView.Travel.NONE,JourneyView.of(snapshot(true,false,"minecraft:overworld",List.of()),OWNER,"minecraft:overworld").travel());
        assertEquals(JourneyView.Travel.NONE,JourneyView.of(null,null,"").travel());
    }
    @Test void chapterTextAndLongHistoryComeFromTheSyncedSnapshot() {
        List<String> journal=java.util.stream.IntStream.range(0,32).mapToObj(i->"记录 "+i).toList();
        var model=JourneyView.of(snapshot(true,true,"",journal),OWNER,REALM);
        assertEquals("本章已完",model.status());assertEquals("当前目标",model.objective());
        assertEquals(journal,model.journal());
        assertThrows(UnsupportedOperationException.class,()->model.journal().add("本地伪造"));
    }
}
