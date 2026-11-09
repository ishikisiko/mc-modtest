package com.example.myvillage.item;

import com.example.myvillage.network.TestUnitPayload;
import com.example.myvillage.network.TestUnitSnapshotPayload;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TestUnitPayloadTest {
    private RegistryFriendlyByteBuf buffer() { return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.NEOFORGE); }
    @Test void requestRoundTripCarriesOnlyOperationAndBoundedConfiguration() {
        var b=buffer();try {
            var payload=new TestUnitPayload("spawn","{\"name\":\"测试修士\"}");
            TestUnitPayload.STREAM_CODEC.encode(b,payload);assertEquals(payload,TestUnitPayload.STREAM_CODEC.decode(b));
        }finally{b.release();}
    }
    @Test void encoderRejectsUnboundedCommandsAndConfiguration() {
        var a=buffer();var b=buffer();try {
            assertThrows(RuntimeException.class,()->TestUnitPayload.STREAM_CODEC.encode(a,new TestUnitPayload("x".repeat(17),"{}")));
            assertThrows(RuntimeException.class,()->TestUnitPayload.STREAM_CODEC.encode(b,new TestUnitPayload("spawn","x".repeat(TestUnitConfig.MAX_JSON+1))));
        }finally{a.release();b.release();}
    }
    @Test void privateSnapshotCanHoldAllBoundedPairOverrides() {
        var data=new com.google.gson.JsonArray();
        for(int i=0;i<64;i++) {
            var row=new com.google.gson.JsonObject();var pairs=new com.google.gson.JsonObject();
            for(int j=0;j<63;j++)pairs.addProperty(java.util.UUID.randomUUID().toString(),false);
            row.add("pairs",pairs);data.add(row);
        }
        var payload=new TestUnitSnapshotPayload(data.toString());var b=buffer();try {
            TestUnitSnapshotPayload.STREAM_CODEC.encode(b,payload);assertEquals(payload,TestUnitSnapshotPayload.STREAM_CODEC.decode(b));
        }finally{b.release();}
    }
}
