package com.example.myvillage.talisman;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TravelPaymentTest {
    @Test void failedReservationNeverAttemptsTravelOrRefundsAnUnpaidCost() {
        var events = new ArrayList<String>();
        assertFalse(TravelPayment.execute(() -> false, () -> { events.add("travel"); return true; },
                () -> events.add("refund"), () -> events.add("complete")));
        assertTrue(events.isEmpty());
    }
    @Test void canceledTravelRestoresExactlyTheReservedCostsAndDoesNotApplyCooldown() {
        var events = new ArrayList<String>();
        assertFalse(TravelPayment.execute(() -> { events.add("reserve"); return true; },
                () -> { events.add("travel"); return false; }, () -> events.add("refund"), () -> events.add("complete")));
        assertEquals(List.of("reserve", "travel", "refund"), events);
    }
    @Test void thrownTravelAlsoRefundsOnce() {
        var events = new ArrayList<String>();
        assertThrows(IllegalStateException.class, () -> TravelPayment.execute(() -> true,
                () -> { throw new IllegalStateException("event failure"); }, () -> events.add("refund"), () -> events.add("complete")));
        assertEquals(List.of("refund"), events);
    }
    @Test void successfulTravelCommitsOnceAndCannotRefundAnAlreadyCompletedTeleport() {
        var events = new ArrayList<String>();
        assertTrue(TravelPayment.execute(() -> true, () -> true, () -> events.add("refund"), () -> events.add("complete")));
        assertEquals(List.of("complete"), events);
        assertThrows(IllegalStateException.class, () -> TravelPayment.execute(() -> true, () -> true,
                () -> events.add("refund"), () -> { throw new IllegalStateException("presentation failure"); }));
        assertEquals(List.of("complete"), events);
    }
}
