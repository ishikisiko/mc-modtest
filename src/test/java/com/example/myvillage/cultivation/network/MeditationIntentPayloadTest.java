package com.example.myvillage.cultivation.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.myvillage.client.cultivation.ClientCultivationState;
import com.example.myvillage.cultivation.CultivationProfile;
import com.example.myvillage.cultivation.meditation.MeditationStatus;
import com.example.myvillage.cultivation.meditation.MeditationStopReason;
import java.lang.reflect.Method;
import java.util.List;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;

class MeditationIntentPayloadTest {
    @Test
    void actionSurfaceIsExactlyTheFourBoundedIntents() {
        assertEquals(
                List.of(
                        MeditationIntentAction.START_NORMAL,
                        MeditationIntentAction.START_SPIRIT,
                        MeditationIntentAction.STOP,
                        MeditationIntentAction.START_BREAKTHROUGH),
                List.of(MeditationIntentAction.values()));
    }

    @Test
    void codecRoundTripsEveryBoundedActionAsOneByte() {
        for (MeditationIntentAction action : MeditationIntentAction.values()) {
            RegistryFriendlyByteBuf buffer = buffer();
            try {
                MeditationIntentPayload payload = new MeditationIntentPayload(action);
                MeditationIntentPayload.STREAM_CODEC.encode(buffer, payload);
                assertEquals(1, buffer.readableBytes());
                assertEquals(payload, MeditationIntentPayload.STREAM_CODEC.decode(buffer));
            } finally {
                buffer.release();
            }
        }
    }

    @Test
    void codecRejectsUnknownAction() {
        RegistryFriendlyByteBuf buffer = buffer();
        try {
            buffer.writeByte(255);
            assertThrows(IllegalArgumentException.class,
                    () -> MeditationIntentPayload.STREAM_CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test
    void clientDisconnectCleanupClearsAllCultivationSnapshots() throws Exception {
        CultivationProfile profile = CultivationProfile.defaultProfile();
        CultivationTimeSnapshotPayload time = new CultivationTimeSnapshotPayload(
                1, 2, 24_000, 6, -1, -1, -1, false);
        MeditationStatus meditation = MeditationStatus.idle(MeditationStopReason.NONE);

        invokeClientState("replace", CultivationProfile.class, profile);
        invokeClientState("replaceTime", CultivationTimeSnapshotPayload.class, time);
        invokeClientState("replaceMeditation", MeditationStatus.class, meditation);
        assertEquals(profile, ClientCultivationState.latest().orElseThrow());
        assertEquals(time, ClientCultivationState.time().orElseThrow());
        assertEquals(meditation, ClientCultivationState.meditation().orElseThrow());

        invokeClientState("clear", null, null);

        assertTrue(ClientCultivationState.latest().isEmpty());
        assertTrue(ClientCultivationState.time().isEmpty());
        assertTrue(ClientCultivationState.meditation().isEmpty());
    }

    private static void invokeClientState(String name, Class<?> parameterType, Object value)
            throws Exception {
        Method method = parameterType == null
                ? ClientCultivationState.class.getDeclaredMethod(name)
                : ClientCultivationState.class.getDeclaredMethod(name, parameterType);
        method.setAccessible(true);
        if (parameterType == null) {
            method.invoke(null);
        } else {
            method.invoke(null, value);
        }
    }

    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(
                Unpooled.buffer(),
                RegistryAccess.EMPTY,
                ConnectionType.NEOFORGE);
    }
}
