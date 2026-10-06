package com.example.myvillage.sim.runtime.net;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** A player's queries closer than four server ticks are dropped; players do not share a budget. */
class WorldSimPayloadThrottleTest {
    @Test
    void queriesUnderFourTicksApartAreDropped() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        assertTrue(WorldSimPayloads.allow(a, 100));
        assertFalse(WorldSimPayloads.allow(a, 100));
        assertFalse(WorldSimPayloads.allow(a, 103));
        assertTrue(WorldSimPayloads.allow(b, 103), "another player has their own budget");
        assertTrue(WorldSimPayloads.allow(a, 100 + WorldSimPayloads.MIN_TICKS_BETWEEN));
        assertFalse(WorldSimPayloads.allow(a, 105));
    }

    @Test
    void aTickCountThatWentBackIsANewServer() {
        UUID a = UUID.randomUUID();
        assertTrue(WorldSimPayloads.allow(a, 5000));
        assertTrue(WorldSimPayloads.allow(a, 2));
        assertFalse(WorldSimPayloads.allow(a, 3));
    }

    @Test
    void manyPlayersStayBounded() {
        UUID first = UUID.randomUUID();
        assertTrue(WorldSimPayloads.allow(first, 10));
        for (int i = 0; i < 1000; i++) {
            assertTrue(WorldSimPayloads.allow(UUID.randomUUID(), 10));
        }
        // the first player was forgotten, so a query of theirs inside the window is answered again
        assertTrue(WorldSimPayloads.allow(first, 11));
    }
}
