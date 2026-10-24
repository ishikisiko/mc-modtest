package com.example.myvillage.story.entrance;

import java.nio.file.Files;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EntranceDataTest {
    @Test void discoveryAndOptInTrackSurviveRestartWithoutAcceptingAnyChapter()throws Exception {
        var r=EntrancePlanTest.rules();var p=EntrancePlanTest.plan(r,-100,-200,r.candidates().getFirst());
        var d=new EntranceData();d.begin(p,Files.readString(EntrancePlanTest.RULES));d.phase=EntranceData.Phase.COMPLETE;
        UUID a=UUID.randomUUID(),b=UUID.randomUUID();assertTrue(d.discover(a));d.track(a,true);
        var loaded=EntranceData.load(d.save(new CompoundTag(),null),null);
        assertEquals(d.plan,loaded.plan);assertTrue(loaded.discovered.contains(a));assertTrue(loaded.tracking.contains(a));
        assertFalse(loaded.discovered.contains(b));assertThrows(IllegalStateException.class,()->loaded.track(b,true));
        loaded.track(a,false);assertTrue(loaded.discovered.contains(a));assertFalse(loaded.tracking.contains(a));
        assertThrows(IllegalStateException.class,()->loaded.begin(p,Files.readString(EntrancePlanTest.RULES)));
    }
    @Test void partialReceiptIsNeverMadeEmptyByRestart()throws Exception {
        var r=EntrancePlanTest.rules();var d=new EntranceData();d.begin(EntrancePlanTest.plan(r,0,0,r.candidates().getFirst()),Files.readString(EntrancePlanTest.RULES));
        for(var phase:new EntranceData.Phase[]{EntranceData.Phase.BUILDING,EntranceData.Phase.FAILED,EntranceData.Phase.UNAVAILABLE}) {
            d.phase=phase;assertEquals(phase,EntranceData.load(d.save(new CompoundTag(),null),null).phase);
        }
    }
    @Test void malformedRoutesOrAreaCannotCauseUnboundedChunkWorkOnLoad()throws Exception {
        var r=EntrancePlanTest.rules();var d=new EntranceData();d.begin(EntrancePlanTest.plan(r,0,0,r.candidates().getFirst()),Files.readString(EntrancePlanTest.RULES));
        var area=d.save(new CompoundTag(),null);area.getCompound("plan").putIntArray("rect",new int[]{Integer.MIN_VALUE,0,Integer.MAX_VALUE,1});
        assertThrows(IllegalStateException.class,()->EntranceData.load(area,null));
        var route=d.save(new CompoundTag(),null);route.getCompound("plan").putIntArray("route",new int[]{0,0,100,100});
        assertThrows(IllegalStateException.class,()->EntranceData.load(route,null));
        var future=d.save(new CompoundTag(),null);future.putInt("schema",2);assertThrows(IllegalStateException.class,()->EntranceData.load(future,null));
    }
}
