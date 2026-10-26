package com.example.myvillage.cultivation.tribulation;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;

class TribulationPayloadTest {
    @Test void ownerStatusAndFixedWarningImpactRoundTrip() {
        var p = new TribulationStatusPayload("11111111-2222-3333-4444-555555555555", "ACTIVE", 2, 2000, 0, 100, 0, 210,
                false, "第3道主雷准备", 10, 64, -20, 12, 12, 48, 4,
                List.of(new TribulationStatusPayload.Mark(12, 64, -18, 2.5, false, false),
                        new TribulationStatusPayload.Mark(10, 64, -20, 2.5, true, true)));
        var b = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.NEOFORGE);
        try { TribulationStatusPayload.STREAM_CODEC.encode(b, p); assertEquals(p, TribulationStatusPayload.STREAM_CODEC.decode(b)); assertEquals(0, b.readableBytes()); }
        finally { b.release(); }
    }
    @Test void payloadBoundsRejectInvalidStrikeReadout() {
        assertThrows(IllegalArgumentException.class, () -> new TribulationStatusPayload("", "ACTIVE", 7, 1, 0, 0, 0, 210, false, "", 0, 0, 0, 12, 12, 48, 4, List.of()));
    }
}
