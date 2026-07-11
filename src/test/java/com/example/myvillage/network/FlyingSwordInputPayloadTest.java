package com.example.myvillage.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FlyingSwordInputPayloadTest {
    @Test
    void packsExactlySixControlFlags() {
        byte flags = FlyingSwordInputFlags.pack(
                true, true, true, true, true, true);

        assertEquals(FlyingSwordInputFlags.ALL, Byte.toUnsignedInt(flags));
        assertTrue(FlyingSwordInputFlags.hasOnlyKnownFlags(flags));
        assertTrue(FlyingSwordInputFlags.has(flags, FlyingSwordInputFlags.FORWARD));
        assertTrue(FlyingSwordInputFlags.has(flags, FlyingSwordInputFlags.DESCEND));
    }

    @Test
    void rejectsUnknownBits() {
        assertFalse(FlyingSwordInputFlags.hasOnlyKnownFlags((byte) 0x40));
    }
}
