package com.example.myvillage.arrival;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ArrivalPayloadTest {
    @Test void boundedChineseDestinationRoundTripsWithoutTrailingBytes() {
        var payload = new ArrivalPayload(ResourceLocation.parse("minecraft:overworld"),
                new ArrivalLocation("zhongzhou", "中州", "capital_zhongzhou", "云山城"), true, true);
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.NEOFORGE);
        try {
            ArrivalPayload.STREAM_CODEC.encode(buffer, payload);
            assertEquals(payload, ArrivalPayload.STREAM_CODEC.decode(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally { buffer.release(); }
    }

    @Test void malformedNamesAndIncompleteIdentitiesFailAtTheBoundary() {
        for (String text : new String[]{"城\n镇", "§4城镇", "城".repeat(65), "   "})
            assertThrows(IllegalArgumentException.class, () -> new ArrivalLocation("r", "洲", "t", text));
        assertThrows(IllegalArgumentException.class, () -> new ArrivalLocation("", "", "t", "城镇"));
        assertThrows(IllegalArgumentException.class, () -> new ArrivalPayload(ResourceLocation.parse("minecraft:overworld"),
                ArrivalLocation.EMPTY, true, false));
    }

    @Test void oversizeWireStringIsRejectedBeforePresentation() {
        var b = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.NEOFORGE);
        try {
            b.writeResourceLocation(ResourceLocation.parse("minecraft:overworld"));
            b.writeUtf("x".repeat(129));
            assertThrows(RuntimeException.class, () -> ArrivalPayload.STREAM_CODEC.decode(b));
        } finally { b.release(); }
    }
}
