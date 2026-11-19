package com.example.myvillage.worldplan.townmap;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TownMapLocationTest {
    private static final String BUNDLE = "0123456789abcdef".repeat(4);

    @Test
    void locationsAndEmptyStateRoundTrip() {
        var origin = new BlockPos.MutableBlockPos(-16_352, 58, 21_344);
        var map = new TownMapLocation("capital_donghai", BUNDLE, origin);
        origin.set(0, 0, 0);
        assertEquals(new BlockPos(-16_352, 58, 21_344), map.origin());
        assertTrue(map.available());
        assertFalse(TownMapLocation.EMPTY.available());
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.NEOFORGE);
        try {
            TownMapLocation.STREAM_CODEC.encode(buffer, map);
            TownMapLocation.STREAM_CODEC.encode(buffer, TownMapLocation.EMPTY);
            assertEquals(map, TownMapLocation.STREAM_CODEC.decode(buffer));
            assertEquals(TownMapLocation.EMPTY, TownMapLocation.STREAM_CODEC.decode(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    @Test
    void rejectUnboundedAndAmbiguousIdentities() {
        assertThrows(IllegalArgumentException.class, () -> new TownMapLocation("../data", BUNDLE, BlockPos.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new TownMapLocation("capital_donghai", "bad", BlockPos.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new TownMapLocation("", BUNDLE, BlockPos.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new TownMapLocation("", "", new BlockPos(1, 0, 0)));
    }
}
