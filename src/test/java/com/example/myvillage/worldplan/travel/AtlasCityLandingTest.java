package com.example.myvillage.worldplan.travel;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AtlasCityLandingTest {
    private final AtlasCityTravelRules rules=AtlasCityTravelRules.bundled();
    @Test void searchStartsAtFirstAirEntryAndNeverUsesAnUnboundedFallback() {
        var entry=new AtlasCityLanding.Point(-17,73,15);
        var candidates=AtlasCityLanding.candidates(entry,rules);
        assertEquals(entry,candidates.getFirst());
        assertEquals((2*rules.searchRadius()+1)*(2*rules.searchRadius()+1)*(2*rules.verticalRadius()+1),candidates.size());
        assertEquals(candidates.size(),new HashSet<>(candidates).size());
        for (var point : candidates) {
            assertTrue(Math.abs(point.x()-entry.x())<=rules.searchRadius());
            assertTrue(Math.abs(point.y()-entry.y())<=rules.verticalRadius());
            assertTrue(Math.abs(point.z()-entry.z())<=rules.searchRadius());
        }
    }
    @Test void chunkBudgetIncludesCollisionAndHazardEdgesAtEveryNegativeAndPositiveAlignment() {
        for (int x=-17;x<=17;x++) for (int z=-17;z<=17;z++) {
            var entry=new AtlasCityLanding.Point(x,64,z);
            var chunks=AtlasCityLanding.chunks(entry,rules);
            assertTrue(chunks.size()<=rules.maxChunksPerRequest());
            Set<AtlasCityLanding.Chunk> coverage=new HashSet<>(chunks);
            int pad=rules.hazardPadding()+1;
            for (int dx : new int[]{-rules.searchRadius()-pad,rules.searchRadius()+pad})
                for (int dz : new int[]{-rules.searchRadius()-pad,rules.searchRadius()+pad})
                    assertTrue(coverage.contains(new AtlasCityLanding.Chunk(Math.floorDiv(x+dx,16),Math.floorDiv(z+dz,16))));
        }
    }
}
