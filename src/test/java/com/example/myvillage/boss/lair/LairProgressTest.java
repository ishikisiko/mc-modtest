package com.example.myvillage.boss.lair;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LairProgressTest {
    @Test void boundOrUnloadedBossNeverCreatesAnotherSlot() {
        var progress = new LairProgress("READY", null);
        UUID boss = UUID.randomUUID();
        assertTrue(progress.bind(boss));
        assertFalse(progress.bind(UUID.randomUUID()));
        var reloaded = new LairProgress(progress.state().name(), progress.boss());
        assertEquals(boss, reloaded.boss());
        assertEquals(LairProgress.State.ACTIVE, reloaded.state());
        assertFalse(reloaded.bind(UUID.randomUUID()));
        assertFalse(reloaded.died(UUID.randomUUID()));
        assertFalse(reloaded.retry(UUID.randomUUID()));
    }
    @Test void peacefulReceiptCanRetryOnlyTheCurrentLiveIdentity() {
        UUID first = UUID.randomUUID(), replacement = UUID.randomUUID();
        var progress = new LairProgress("ACTIVE", first);
        assertTrue(progress.retry(first));
        assertFalse(progress.retry(first));
        assertTrue(progress.bind(replacement));
        assertFalse(progress.died(first));
        assertTrue(progress.died(replacement));
        assertFalse(progress.retry(replacement));
        assertFalse(progress.bind(UUID.randomUUID()));
    }
    @Test void realDeathIsAPermanentSavedTombstone() {
        UUID boss = UUID.randomUUID();
        var progress = new LairProgress("ACTIVE", boss);
        assertTrue(progress.died(boss));
        var reloaded = new LairProgress(progress.state().name(), progress.boss());
        assertEquals(LairProgress.State.CLEARED, reloaded.state());
        assertEquals(boss, reloaded.boss());
        assertFalse(reloaded.bind(UUID.randomUUID()));
        assertFalse(reloaded.retry(boss));
        assertFalse(reloaded.died(boss));
    }
    @Test void malformedOrHandPlacedAnchorsFailClosed() {
        for (var progress : new LairProgress[] {new LairProgress("", null),
                new LairProgress("ACTIVE", null), new LairProgress("CLEARED", null),
                new LairProgress("READY", UUID.randomUUID()), new LairProgress("DORMANT", UUID.randomUUID())}) {
            assertEquals(LairProgress.State.DORMANT, progress.state());
            assertNull(progress.boss());
            assertFalse(progress.bind(UUID.randomUUID()));
        }
    }
}
