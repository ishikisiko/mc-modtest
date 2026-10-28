package com.example.myvillage.worldplan.travel;

import java.util.ArrayList;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static com.example.myvillage.worldplan.travel.AtlasCityTravelLimiter.Decision.*;
import static org.junit.jupiter.api.Assertions.*;

class AtlasCityTravelLimiterTest {
    private final AtlasCityTravelRules rules=AtlasCityTravelRules.bundled();
    @Test void duplicateAndCapacityRefusalsNeverQueueOrReplaceAnActiveRequest() {
        var limiter=new AtlasCityTravelLimiter();var players=new ArrayList<UUID>();
        for (int i=0;i<rules.maxConcurrentRequests();i++) {
            UUID player=UUID.randomUUID();players.add(player);
            assertEquals(ACCEPTED,limiter.admit(player,0,rules));
        }
        assertEquals(IN_FLIGHT,limiter.admit(players.getFirst(),0,rules));
        assertEquals(FULL,limiter.admit(UUID.randomUUID(),0,rules));
        assertEquals(rules.maxConcurrentRequests(),limiter.activeCount());
        limiter.release(players.getFirst());
        assertEquals(RATE_LIMITED,limiter.admit(players.getFirst(),1,rules));
        assertEquals(ACCEPTED,limiter.admit(players.getFirst(),rules.requestCooldownTicks(),rules));
    }
    @Test void failureLogoutAndStopReleaseSlotsAndExpiredCooldowns() {
        var limiter=new AtlasCityTravelLimiter();UUID player=UUID.randomUUID();
        assertEquals(ACCEPTED,limiter.admit(player,0,rules));
        limiter.release(player);assertEquals(0,limiter.activeCount());
        assertEquals(RATE_LIMITED,limiter.admit(player,1,rules));
        limiter.forget(player);assertEquals(ACCEPTED,limiter.admit(player,1,rules));
        limiter.clear();assertEquals(0,limiter.activeCount());
        assertEquals(ACCEPTED,limiter.admit(player,2,rules));
        limiter.release(player);limiter.prune(2+rules.requestCooldownTicks());
        assertEquals(ACCEPTED,limiter.admit(player,2+rules.requestCooldownTicks(),rules));
    }
    @Test void rejectionFeedbackIsBoundedSeparatelyFromAdmission() {
        var limiter=new AtlasCityTravelLimiter();UUID player=UUID.randomUUID();
        assertTrue(limiter.feedback(player,0,rules));
        assertFalse(limiter.feedback(player,1,rules));
        assertTrue(limiter.feedback(player,rules.feedbackIntervalTicks(),rules));
    }
}
