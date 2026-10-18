package com.example.myvillage.sim.runtime.commerce;

import io.netty.buffer.Unpooled;
import java.util.Arrays;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CommercePayloadTest {
    @Test void onlyBoundedIntentCrossesWireAndEveryNonceBitRoundTrips() {
        assertEquals(java.util.List.of("menuId","token","sequence","offer","quantity"),
                Arrays.stream(CommerceIntentPayload.class.getRecordComponents()).map(c->c.getName()).toList());
        for(long nonce:new long[]{0,Long.MIN_VALUE,Long.MAX_VALUE}) {
            var message=new CommerceIntentPayload(100,nonce,30_000,31,20_000);
            var buf=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY,ConnectionType.NEOFORGE);
            try {CommerceIntentPayload.STREAM_CODEC.encode(buf,message);
                assertEquals(message,CommerceIntentPayload.STREAM_CODEC.decode(buf));assertEquals(0,buf.readableBytes());
            } finally {buf.release();}
        }
    }
    @Test void invalidQuantityIsRejectedDuringDecodeToo() {
        for(int quantity:new int[]{-1,0,20_001,Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class,()->new CommerceIntentPayload(1,0,0,0,quantity));
            var buf=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY,ConnectionType.NEOFORGE);
            try {
                buf.writeVarInt(1);buf.writeVarLong(0);buf.writeVarInt(0);buf.writeVarInt(0);buf.writeVarInt(quantity);
                assertThrows(IllegalArgumentException.class,()->CommerceIntentPayload.STREAM_CODEC.decode(buf));
            } finally {buf.release();}
        }
    }
    @Test void invalidMenuSequenceAndOfferAreRejected() {
        assertThrows(IllegalArgumentException.class,()->new CommerceIntentPayload(-1,0,0,0,1));
        assertThrows(IllegalArgumentException.class,()->new CommerceIntentPayload(256,0,0,0,1));
        assertThrows(IllegalArgumentException.class,()->new CommerceIntentPayload(1,0,-1,0,1));
        assertThrows(IllegalArgumentException.class,()->new CommerceIntentPayload(1,0,30_001,0,1));
        assertThrows(IllegalArgumentException.class,()->new CommerceIntentPayload(1,0,1,-1,1));
        assertThrows(IllegalArgumentException.class,()->new CommerceIntentPayload(1,0,1,32,1));
    }
}
