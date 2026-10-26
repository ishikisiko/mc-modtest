package com.example.myvillage.cultivation.network;

import com.example.myvillage.cultivation.meditation.*;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TechniqueGrowthPayloadTest {
    private static RegistryFriendlyByteBuf buffer() { return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.NEOFORGE); }
    private static final ResourceLocation ID = ResourceLocation.parse("myvillage:basic_breathing");
    @Test void boundedIntentRoundTripsAndRejectsClientInventedActions() {
        for (int action = 0; action < 3; action++) {
            var expected = new TechniqueGrowthIntentPayload(ID, action, 123);
            var b = buffer();
            try { TechniqueGrowthIntentPayload.STREAM_CODEC.encode(b, expected);
                assertEquals(expected, TechniqueGrowthIntentPayload.STREAM_CODEC.decode(b)); assertEquals(0, b.readableBytes());
            } finally { b.release(); }
        }
        assertThrows(IllegalArgumentException.class, () -> new TechniqueGrowthIntentPayload(ID, 3, 0));
        assertThrows(IllegalArgumentException.class, () -> new TechniqueGrowthIntentPayload(ID, 0, -1));
    }
    @Test void practiceStatusRoundTripsWithoutPretendingToBeAManual() {
        for (var state : new MeditationState[]{MeditationState.PREPARING_NORMAL, MeditationState.MEDITATING_NORMAL}) {
            var status = MeditationStatus.practice(state, state.preparing() ? 40 : 0, MeditationStopReason.PRACTICE_ACCEPTED, ID);
            var b = buffer();
            try { MeditationStatusPayload.STREAM_CODEC.encode(b, MeditationStatusPayload.fromStatus(status));
                var actual = MeditationStatusPayload.STREAM_CODEC.decode(b);
                assertEquals(status, actual.status()); assertTrue(actual.study().isEmpty()); assertEquals(0, b.readableBytes());
            } finally { b.release(); }
        }
    }
}
