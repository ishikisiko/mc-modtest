package com.example.myvillage.sim;

import java.util.List;

/** Read-only region summary: who is here, which sects are seated here, recent notable events. */
public record RegionView(
        String id,
        String displayName,
        int tier,
        int qiLo,
        int qiHi,
        int dangerLo,
        int dangerHi,
        boolean admitsSects,
        int livingCount,
        List<Integer> sectIds,
        List<SimEvent> recentEvents) {
}
