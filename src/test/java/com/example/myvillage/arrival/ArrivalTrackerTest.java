package com.example.myvillage.arrival;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ArrivalTrackerTest {
    private static final ArrivalLocation CITY = new ArrivalLocation("zhongzhou", "中州", "capital_zhongzhou", "云山城");
    private static final ArrivalLocation FIELD = new ArrivalLocation("zhongzhou", "中州", "", "");
    private static final ArrivalLocation OTHER = new ArrivalLocation("ximo", "西漠", "capital_ximo", "西漠城");
    private static final ArrivalRules R = ArrivalRules.INSTANCE;

    @Test void initialCityWaitsForStablePositionThenOffersBothCardsOnce() {
        var t = new ArrivalTracker(R);
        assertTrue(t.observe(CITY, 0).isEmpty());
        assertTrue(t.observe(CITY, R.settleTicks() - 1).isEmpty());
        var update = t.observe(CITY, R.settleTicks()).orElseThrow();
        assertTrue(update.showRegion()); assertTrue(update.showTown());
        assertTrue(t.observe(CITY, 10000).isEmpty(), "staying, same-city teleport and same-place respawn never replay");
    }

    @Test void shortExcursionAcrossCityEdgeDoesNotBecomeAnotherEntry() {
        var t = new ArrivalTracker(R); t.observe(CITY, 0); t.observe(CITY, R.settleTicks());
        t.observe(FIELD, 100);
        assertTrue(t.observe(FIELD, 100 + R.exitGraceTicks() - 1).isEmpty());
        assertTrue(t.observe(CITY, 200).isEmpty());
        assertEquals(CITY, t.current());
    }

    @Test void cooldownSuppressesRapidReentryButLaterVisitsCanReplay() {
        var t = new ArrivalTracker(R); t.observe(CITY, 0); t.observe(CITY, R.settleTicks());
        t.observe(FIELD, 100); t.observe(FIELD, 100 + R.exitGraceTicks());
        t.observe(CITY, 200);
        var suppressed = t.observe(CITY, 200 + R.settleTicks()).orElseThrow();
        assertFalse(suppressed.showRegion()); assertFalse(suppressed.showTown());
        long later = R.repeatCooldownTicks() + 100;
        t.observe(FIELD, later); t.observe(FIELD, later + R.exitGraceTicks());
        t.observe(CITY, later + R.exitGraceTicks() + 5);
        var replay = t.observe(CITY, later + R.exitGraceTicks() + 5 + R.settleTicks()).orElseThrow();
        assertFalse(replay.showRegion()); assertTrue(replay.showTown());
    }

    @Test void enteringTownWithinCurrentProvinceOnlyOffersTown() {
        var t = new ArrivalTracker(R); t.observe(FIELD, 0); t.observe(FIELD, R.settleTicks());
        t.observe(CITY, 100);
        var update = t.observe(CITY, 100 + R.settleTicks()).orElseThrow();
        assertFalse(update.showRegion()); assertTrue(update.showTown());
    }

    @Test void rapidProvinceBoundaryFlappingNeverConfirmsWrongDestination() {
        var t = new ArrivalTracker(R); t.observe(CITY, 0); t.observe(CITY, R.settleTicks());
        for (int tick = 100; tick < 250; tick += 5)
            assertTrue(t.observe(tick % 10 == 0 ? OTHER : CITY, tick).isEmpty());
        t.observe(OTHER, 300);
        var update = t.observe(OTHER, 300 + R.settleTicks()).orElseThrow();
        assertTrue(update.showRegion()); assertTrue(update.showTown()); assertEquals(OTHER, t.current());
    }

    @Test void leavingTheOverworldClearsDestinationWithoutInventingAProvince() {
        var t = new ArrivalTracker(R); t.observe(CITY, 0); t.observe(CITY, R.settleTicks());
        t.observe(ArrivalLocation.EMPTY, 100);
        var update = t.observe(ArrivalLocation.EMPTY, 100 + R.exitGraceTicks()).orElseThrow();
        assertFalse(update.showRegion()); assertFalse(update.showTown()); assertEquals(ArrivalLocation.EMPTY, t.current());
    }
}
