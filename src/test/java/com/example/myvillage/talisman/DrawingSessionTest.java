package com.example.myvillage.talisman;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DrawingSessionTest {
    @Test void aHeartbeatNeverGrantsAnInfiniteProductionLease() {
        DrawingSession s = new DrawingSession(); s.heartbeat(100, 7);
        assertTrue(s.holding(106)); assertFalse(s.holding(107)); assertFalse(s.due(108));
        s.heartbeat(110, 7); assertTrue(s.due(110)); s.stop(); assertFalse(s.due(110));
        s.reset(); assertFalse(s.holding(111));
    }
    @Test void blockedOutputMissingMaterialExpiredHoldOrRejectedPaymentNeverCommitOrDoubleCharge() {
        DrawingSession s = new DrawingSession(); s.heartbeat(100, 7);
        AtomicInteger paid = new AtomicInteger(), committed = new AtomicInteger();
        var payment = (java.util.function.BooleanSupplier) () -> { paid.incrementAndGet(); return true; };
        assertFalse(s.pulse(100, 5, true, false, payment, committed::incrementAndGet));
        assertFalse(s.pulse(100, 5, false, true, payment, committed::incrementAndGet));
        assertFalse(s.pulse(107, 5, true, true, payment, committed::incrementAndGet));
        assertEquals(0, paid.get()); assertEquals(0, committed.get());
        s.heartbeat(110, 7);
        assertFalse(s.pulse(110, 5, true, true, () -> false, committed::incrementAndGet));
        assertEquals(0, committed.get());
        assertTrue(s.pulse(110, 5, true, true, payment, committed::incrementAndGet));
        assertFalse(s.pulse(110, 5, true, true, payment, committed::incrementAndGet));
        assertEquals(1, paid.get()); assertEquals(1, committed.get());
    }
    @Test void stopAndResumePreservePaidWorkWithoutCatchup() {
        DrawingSession s = new DrawingSession(); AtomicInteger progress = new AtomicInteger();
        s.heartbeat(10, 7); assertTrue(s.pulse(10, 5, true, true, () -> true, progress::incrementAndGet));
        s.stop(); assertFalse(s.pulse(999, 5, true, true, () -> true, progress::incrementAndGet));
        s.heartbeat(1000, 7); assertTrue(s.pulse(1000, 5, true, true, () -> true, progress::incrementAndGet));
        assertEquals(2, progress.get()); // No offline/closed-menu catchup.
    }
}
