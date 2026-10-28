package com.example.myvillage.network;

import com.example.myvillage.worldplan.travel.AtlasCityTravelTarget;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AtlasCityTravelPayloadTest {
    @Test void boundedIntentRoundTripsWithoutCoordinatesOrTrailingBytes() {
        var payload=new AtlasCityTravelPayload("capital_"+"a".repeat(48),"f".repeat(64));
        var buffer=new FriendlyByteBuf(Unpooled.buffer());
        try {
            AtlasCityTravelPayload.STREAM_CODEC.encode(buffer,payload);
            assertTrue(buffer.readableBytes()<=AtlasCityTravelTarget.MAX_CITY_ID+64+2);
            assertEquals(payload,AtlasCityTravelPayload.STREAM_CODEC.decode(buffer));
            assertEquals(0,buffer.readableBytes());
            assertEquals(2,AtlasCityTravelPayload.class.getRecordComponents().length);
        } finally { buffer.release(); }
    }
    @Test void invalidIdsAndFingerprintsCannotBecomeRequests() {
        for (String city : new String[]{"", "zhongzhou", "capital_", "capital_../moyu", "capital_Donghai", "capital_"+"a".repeat(49)})
            assertThrows(IllegalArgumentException.class,() -> new AtlasCityTravelPayload(city,"a".repeat(64)));
        for (String fingerprint : new String[]{"", "a".repeat(63), "a".repeat(65), "A".repeat(64), "z".repeat(64)})
            assertThrows(IllegalArgumentException.class,() -> new AtlasCityTravelPayload("capital_donghai",fingerprint));
    }
    @Test void wireLengthLimitsRejectBeforeDispatch() {
        for (boolean oversizeCity : new boolean[]{true,false}) {
            var buffer=new FriendlyByteBuf(Unpooled.buffer());
            try {
                buffer.writeUtf(oversizeCity ? "capital_"+"a".repeat(49) : "capital_donghai");
                buffer.writeUtf("f".repeat(oversizeCity ? 64 : 65));
                assertThrows(RuntimeException.class,() -> AtlasCityTravelPayload.STREAM_CODEC.decode(buffer));
            } finally { buffer.release(); }
        }
    }
}
