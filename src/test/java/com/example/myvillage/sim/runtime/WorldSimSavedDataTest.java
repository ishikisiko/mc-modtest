package com.example.myvillage.sim.runtime;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

/** The NBT wrapper around the core payload: round trip, and a save it cannot read is left alone. */
class WorldSimSavedDataTest {
    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void aFreshWorldHasNoLedgerAndWritesNoPayload() {
        WorldSimSavedData fresh = new WorldSimSavedData();
        assertFalse(fresh.loadedFromDisk());
        assertFalse(fresh.hasPayload());
        CompoundTag tag = fresh.save(new CompoundTag(), null);
        assertFalse(tag.contains(WorldSimSavedData.PAYLOAD_TAG));
        WorldSimSavedData loaded = WorldSimSavedData.load(tag, null);
        assertTrue(loaded.loadedFromDisk());
        assertFalse(loaded.hasPayload());
        assertNull(loaded.payload());
    }

    @Test
    void anAttachedLedgerRoundTripsWithItsTierAndPausedMirror() {
        WorldSimSavedData data = new WorldSimSavedData();
        byte[] payload = bytes("{\"format\":\"myvillage:world_sim\",\"version\":1}");
        data.attach(() -> new WorldSimSavedData.Snapshot("small", true, payload));
        assertTrue(data.isDirty());
        CompoundTag tag = data.save(new CompoundTag(), null);
        WorldSimSavedData loaded = WorldSimSavedData.load(tag, null);
        assertEquals(WorldSimSavedData.FORMAT, loaded.format());
        assertFalse(loaded.newerFormat());
        assertEquals("small", loaded.tier());
        assertTrue(loaded.pausedMirror());
        assertArrayEquals(payload, loaded.payload());
    }

    @Test
    void aNewerFormatIsWrittenBackUntouched() {
        CompoundTag tag = new CompoundTag();
        tag.putInt(WorldSimSavedData.FORMAT_TAG, WorldSimSavedData.FORMAT + 7);
        tag.putString(WorldSimSavedData.TIER_TAG, "large");
        tag.putByteArray(WorldSimSavedData.PAYLOAD_TAG, bytes("future"));
        WorldSimSavedData loaded = WorldSimSavedData.load(tag, null);
        assertTrue(loaded.newerFormat());
        CompoundTag again = loaded.save(new CompoundTag(), null);
        assertEquals(tag, again);
    }

    @Test
    void detachKeepsTheLastCheckpointNotTheHalfSettledState() {
        WorldSimSavedData data = new WorldSimSavedData();
        AtomicReference<byte[]> live = new AtomicReference<>(bytes("day-10"));
        data.attach(() -> new WorldSimSavedData.Snapshot("small", false, live.get()));
        data.save(new CompoundTag(), null);
        live.set(bytes("half-settled"));
        data.detach();
        assertFalse(data.attached());
        CompoundTag tag = data.save(new CompoundTag(), null);
        assertArrayEquals(bytes("day-10"), tag.getByteArray(WorldSimSavedData.PAYLOAD_TAG));
    }

    @Test
    void theRealCorePayloadSurvivesTheWrapper() {
        byte[] payload = RuntimeFixtures.world().toBytes();
        WorldSimSavedData data = new WorldSimSavedData();
        data.attach(() -> new WorldSimSavedData.Snapshot("small", false, payload));
        WorldSimSavedData loaded = WorldSimSavedData.load(data.save(new CompoundTag(), null), null);
        assertArrayEquals(payload, loaded.payload());
    }
}
