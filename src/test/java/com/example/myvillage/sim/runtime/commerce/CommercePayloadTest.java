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
        assertEquals(java.util.List.of("menuId","token","sequence","offer"),
                Arrays.stream(CommerceIntentPayload.class.getRecordComponents()).map(c->c.getName()).toList());
        for(long nonce:new long[]{0,Long.MIN_VALUE,Long.MAX_VALUE}) {
            var message=new CommerceIntentPayload(100,nonce,30_000,31);
            var buf=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY,ConnectionType.NEOFORGE);
            try {CommerceIntentPayload.STREAM_CODEC.encode(buf,message);
                assertEquals(message,CommerceIntentPayload.STREAM_CODEC.decode(buf));assertEquals(0,buf.readableBytes());
            } finally {buf.release();}
        }
    }
    @Test void invalidMenuSequenceAndOfferAreRejected() {
        assertThrows(IllegalArgumentException.class,()->new CommerceIntentPayload(-1,0,0,0));
        assertThrows(IllegalArgumentException.class,()->new CommerceIntentPayload(256,0,0,0));
        assertThrows(IllegalArgumentException.class,()->new CommerceIntentPayload(1,0,-1,0));
        assertThrows(IllegalArgumentException.class,()->new CommerceIntentPayload(1,0,30_001,0));
        assertThrows(IllegalArgumentException.class,()->new CommerceIntentPayload(1,0,1,-1));
        assertThrows(IllegalArgumentException.class,()->new CommerceIntentPayload(1,0,1,32));
    }
}
