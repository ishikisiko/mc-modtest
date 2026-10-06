package com.example.myvillage.cultivation.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.myvillage.cultivation.meditation.MeditationState;
import com.example.myvillage.cultivation.meditation.MeditationStatus;
import com.example.myvillage.cultivation.meditation.MeditationStopReason;
import com.example.myvillage.cultivation.meditation.StudyProgress;
import com.example.myvillage.cultivation.data.AdvancementKind;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
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
    void streamCodecRoundTripsStudyProgressWithAndWithoutANextGate() {
        ResourceLocation technique = ResourceLocation.fromNamespaceAndPath("myvillage", "gengjin_jianjue");
        for (StudyProgress progress : new StudyProgress[]{
                new StudyProgress(technique, 5_995, 12_000, 6_000, 50),
                new StudyProgress(technique, 0, 4_000, StudyProgress.NO_GATE, 0)}) {
            for (MeditationStatus status : new MeditationStatus[]{
                    MeditationStatus.study(MeditationState.PREPARING_NORMAL, 40,
                            MeditationStopReason.STUDY_ACCEPTED, progress),
                    MeditationStatus.study(MeditationState.MEDITATING_NORMAL, 0,
                            MeditationStopReason.NONE, progress)}) {
                MeditationStatusPayload payload = MeditationStatusPayload.fromStatus(status);
                RegistryFriendlyByteBuf buffer = buffer();
                try {
                    MeditationStatusPayload.STREAM_CODEC.encode(buffer, payload);
                    MeditationStatusPayload decoded = MeditationStatusPayload.STREAM_CODEC.decode(buffer);
                    assertEquals(payload, decoded);
                    assertEquals(status, decoded.status());
                    assertEquals(0, buffer.readableBytes());
                } finally {
                    buffer.release();
                }
            }
        }
    }

    @Test
    void stoppedStudyStatusesTravelWithoutProgress() {
        for (MeditationStopReason reason : new MeditationStopReason[]{
                MeditationStopReason.MANUAL_LOST, MeditationStopReason.STUDY_GATE,
                MeditationStopReason.STUDY_COMPLETE, MeditationStopReason.STUDY_REQUIREMENTS}) {
            MeditationStatusPayload payload = MeditationStatusPayload.fromStatus(MeditationStatus.idle(reason));
            RegistryFriendlyByteBuf buffer = buffer();
            try {
                MeditationStatusPayload.STREAM_CODEC.encode(buffer, payload);
                MeditationStatusPayload decoded = MeditationStatusPayload.STREAM_CODEC.decode(buffer);
                assertEquals(reason, decoded.reason());
                assertTrue(decoded.study().isEmpty());
                assertEquals(0, buffer.readableBytes());
            } finally {
                buffer.release();
            }
        }
    }

    @Test
    void rejectsStudyProgressOutsideAStudyState() {
        ResourceLocation technique = ResourceLocation.fromNamespaceAndPath("myvillage", "gengjin_jianjue");
        assertThrows(IllegalArgumentException.class, () -> new MeditationStatusPayload(
                MeditationState.MEDITATING_SPIRIT, 0, MeditationStopReason.NONE, Optional.empty(), 0, 0,
                Optional.of(new StudyProgress(technique, 0, 4_000, -1, 0))));
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
