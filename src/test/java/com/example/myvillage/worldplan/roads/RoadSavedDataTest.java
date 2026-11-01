package com.example.myvillage.worldplan.roads;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RoadSavedDataTest {
    @Test void restartPreservesHalfHeightsAndExistingGateButPausesWrites() {
        RoadSavedData original=new RoadSavedData();
        original.plan=new RoadPlan("nanjiang","ximo","world","rules",5,Map.of("nanjiang",3,"ximo",5),
            List.of(new RoadAStar.Point(-1,-1),new RoadAStar.Point(2,2)),
            List.of(new RoadPlan.Cell(-1,-1,171,true),new RoadPlan.Cell(2,2,172,false)));
        original.phase="building";original.completedChunks=1;
        var loaded=RoadSavedData.load(original.save(new CompoundTag(),null),null);
        assertEquals(original.plan,loaded.plan);assertEquals("paused",loaded.phase);assertEquals(1,loaded.completedChunks);
    }
    @Test void completedRouteDoesNotBecomeAnAutomaticRebuild() {
        RoadSavedData original=new RoadSavedData();original.phase="complete";
        assertEquals("complete",RoadSavedData.load(original.save(new CompoundTag(),null),null).phase);
    }
    @Test void truncatedFootprintIsRejected() {
        var tag=new CompoundTag();tag.putInt("schema",1);tag.putString("phase","ready");tag.putIntArray("cells",new int[]{1,2,3});
        assertThrows(IllegalStateException.class,()->RoadSavedData.load(tag,null));
    }
}
