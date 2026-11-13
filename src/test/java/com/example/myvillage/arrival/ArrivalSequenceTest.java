package com.example.myvillage.arrival;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ArrivalSequenceTest {
    private static final ArrivalRules R = ArrivalRules.INSTANCE;
    private static final ArrivalLocation CITY = new ArrivalLocation("zhongzhou", "中州", "capital_zhongzhou", "云山城");
    private static final ArrivalLocation OTHER_TOWN = new ArrivalLocation("zhongzhou", "中州", "bay_1", "湾口台地集");

    @Test void provinceAndTownAreSequentialAndExpireWithoutOverlap() {
        var s = new ArrivalSequence(R); s.accept(CITY, true, true); s.tick();
        assertEquals(ArrivalSequence.Kind.REGION, s.current().orElseThrow().kind());
        for (int i = 0; i < R.regionDurationTicks(); i++) s.tick();
        assertTrue(s.current().isEmpty());
        for (int i = 0; i < R.gapTicks(); i++) s.tick();
        assertEquals(ArrivalSequence.Kind.TOWN, s.current().orElseThrow().kind());
        for (int i = 0; i < R.townDurationTicks() + R.gapTicks(); i++) s.tick();
        assertTrue(s.current().isEmpty());
    }

    @Test void aNewTownRemovesTheOldQueuedTownWhileProvinceIsStillVisible() {
        var s = new ArrivalSequence(R); s.accept(CITY, true, true); s.tick();
        s.accept(OTHER_TOWN, false, true);
        for (int i = 0; i < R.regionDurationTicks() + R.gapTicks(); i++) s.tick();
        assertEquals("湾口台地集", s.current().orElseThrow().name());
    }

    @Test void destinationInvalidationAndDisconnectRemoveEveryCard() {
        var s = new ArrivalSequence(R); s.accept(CITY, true, true); s.tick();
        s.accept(ArrivalLocation.EMPTY, false, false); s.tick();
        assertTrue(s.current().isEmpty());
        s.accept(CITY, true, true); s.tick(); s.clear();
        for (int i = 0; i < 200; i++) s.tick();
        assertTrue(s.current().isEmpty());
    }

    @Test void fadeHasTransparentEndpointsAndAnOpaqueHold() {
        var s = new ArrivalSequence(R); s.accept(CITY, true, false); s.tick();
        assertEquals(0, s.opacity(0));
        for (int i = 0; i < R.fadeInTicks(); i++) s.tick();
        assertEquals(1, s.opacity(0));
        for (int i = R.fadeInTicks(); i < R.regionDurationTicks() - 1; i++) s.tick();
        assertTrue(s.opacity(.9f) < .01f);
        s.tick(); assertEquals(0, s.opacity(0));
    }
}
