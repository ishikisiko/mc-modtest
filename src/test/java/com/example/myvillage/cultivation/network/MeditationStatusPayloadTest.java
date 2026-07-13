package com.example.myvillage.cultivation.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.myvillage.cultivation.meditation.MeditationState;
import com.example.myvillage.cultivation.meditation.MeditationStopReason;
import com.example.myvillage.cultivation.data.AdvancementKind;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;

import java.util.Optional;

class MeditationStatusPayloadTest {
    @Test
    void streamCodecRoundTripsTransitionStatus() {
        MeditationStatusPayload payload = new MeditationStatusPayload(
                MeditationState.PREPARING_SPIRIT,
                27,
                MeditationStopReason.START_ACCEPTED);
        RegistryFriendlyByteBuf buffer = buffer();
        try {
            MeditationStatusPayload.STREAM_CODEC.encode(buffer, payload);
            assertEquals(payload, MeditationStatusPayload.STREAM_CODEC.decode(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    @Test
    void streamCodecRoundTripsAdvancementProgressStatus() {
        MeditationStatusPayload payload = new MeditationStatusPayload(
                MeditationState.ADVANCING_BOTTLENECK,
                0,
                MeditationStopReason.NONE,
                Optional.of(AdvancementKind.BOTTLENECK),
                200,
                137);
        RegistryFriendlyByteBuf buffer = buffer();
        try {
            MeditationStatusPayload.STREAM_CODEC.encode(buffer, payload);
            MeditationStatusPayload decoded = MeditationStatusPayload.STREAM_CODEC.decode(buffer);
            assertEquals(payload, decoded);
            assertEquals(payload.status(), decoded.status());
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    @Test
    void rejectsUnknownEnumNetworkId() {
        RegistryFriendlyByteBuf buffer = buffer();
        try {
            buffer.writeVarInt(999);
            buffer.writeVarInt(0);
            buffer.writeVarInt(0);
            assertThrows(IllegalArgumentException.class,
                    () -> MeditationStatusPayload.STREAM_CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test
    void rejectsMismatchedAdvancementStateAndKind() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new MeditationStatusPayload(
                        MeditationState.ADVANCING_ORDINARY,
                        0,
                        MeditationStopReason.NONE,
                        Optional.of(AdvancementKind.BOTTLENECK),
                        100,
                        100));
    }

    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(
                Unpooled.buffer(),
                RegistryAccess.EMPTY,
                ConnectionType.NEOFORGE);
    }
}
