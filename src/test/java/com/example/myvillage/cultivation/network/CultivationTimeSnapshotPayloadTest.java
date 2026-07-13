package com.example.myvillage.cultivation.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.netty.buffer.Unpooled;
import com.example.myvillage.cultivation.time.CultivationTimeStatus;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;

class CultivationTimeSnapshotPayloadTest {
    @Test
    void streamCodecRoundTripsAvailableStatus() {
        CultivationTimeSnapshotPayload payload = new CultivationTimeSnapshotPayload(
                3_456_789L,
                1_234_567L,
                24_000L,
                6,
                80,
                11_520_000L,
                10_285_433L,
                false);
        RegistryFriendlyByteBuf buffer = buffer();
        try {
            CultivationTimeSnapshotPayload.STREAM_CODEC.encode(buffer, payload);
            assertEquals(payload, CultivationTimeSnapshotPayload.STREAM_CODEC.decode(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    @Test
    void streamCodecRoundTripsUnavailableRealmStatus() {
        CultivationTimeSnapshotPayload payload = new CultivationTimeSnapshotPayload(
                0, 42, 24_000, 6, -1, -1, -1, false);
        RegistryFriendlyByteBuf buffer = buffer();
        try {
            CultivationTimeSnapshotPayload.STREAM_CODEC.encode(buffer, payload);
            assertEquals(payload, CultivationTimeSnapshotPayload.STREAM_CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test
    void rejectsPartialOrExhaustedUnavailableStatus() {
        assertThrows(IllegalArgumentException.class, () -> new CultivationTimeSnapshotPayload(
                0, 0, 24_000, 6, -1, 0, -1, false));
        assertThrows(IllegalArgumentException.class, () -> new CultivationTimeSnapshotPayload(
                0, 0, 24_000, 6, -1, -1, -1, true));
    }

    @Test
    void mapsAuthoritativeResolvedAndUnresolvedStatuses() {
        CultivationTimeSnapshotPayload resolved = CultivationTimeSnapshotPayload.fromStatus(
                new CultivationTimeStatus(
                        100, 200, 24_000, 6, true, 11_520_000, 11_519_800, false));
        assertEquals(80, resolved.maximumLifespanYears());
        assertEquals(11_519_800, resolved.remainingLifespanTicks());

        CultivationTimeSnapshotPayload unresolved = CultivationTimeSnapshotPayload.fromStatus(
                new CultivationTimeStatus(100, 200, 24_000, 6, false, 0, 0, false));
        assertEquals(-1, unresolved.maximumLifespanYears());
        assertEquals(-1, unresolved.remainingLifespanTicks());
    }

    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(
                Unpooled.buffer(),
                RegistryAccess.EMPTY,
                ConnectionType.NEOFORGE);
    }
}
