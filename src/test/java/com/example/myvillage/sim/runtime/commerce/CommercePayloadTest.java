package com.example.myvillage.sim.runtime.commerce;

import io.netty.buffer.Unpooled;
import java.util.Arrays;
import java.nio.file.*;
import com.google.gson.JsonParser;
import com.example.myvillage.sim.commerce.CommerceRules;
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
            var message=new CommerceIntentPayload(100,nonce,30_000,63,20_000);
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
        assertThrows(IllegalArgumentException.class,()->new CommerceIntentPayload(1,0,1,64,1));
    }
    @Test void openingSnapshotCarriesEveryDisplayedQualityAndItsServerPrice() throws Exception {
        var rules=CommerceRules.parse("test",JsonParser.parseString(Files.readString(Path.of("src/main/resources/data/myvillage/world_sim/commerce_rules.json"))).getAsJsonObject());
        var offers=rules.offers("herbalist");assertTrue(offers.size()>32);assertTrue(offers.size()<=CommerceRules.MAX_OFFERS);
        var buf=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY,ConnectionType.NEOFORGE);
        try {
            CommerceMenu.writeOpening(buf,123,rules.currency(),rules.stockLimit(),offers);
            assertEquals(123,buf.readLong());assertEquals(rules.currency(),buf.readUtf(160));assertEquals(rules.stockLimit(),buf.readVarInt());
            var decoded=CommerceMenu.readOffers(buf);assertEquals(offers.size(),decoded.size());
            for(int i=0;i<offers.size();i++) {
                var expected=offers.get(i);var actual=decoded.get(i);
                assertEquals(expected.id(),actual.id());assertEquals(expected.item(),actual.item());assertEquals(expected.kind(),actual.kind());
                assertEquals(expected.quantity(),actual.quantity());assertEquals(expected.price(),actual.price());assertEquals(expected.pillQuality(),actual.pillQuality());
            }
            assertEquals(0,buf.readableBytes());
        }finally{buf.release();}
    }
    @Test void invalidDisplayQualityAndOversizedCatalogueAreRejected() {
        var buf=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY,ConnectionType.NEOFORGE);
        try {
            buf.writeVarInt(1);buf.writeUtf("pill");buf.writeUtf("herbalist");buf.writeUtf("sell");buf.writeUtf("myvillage:pill_huiling");
            buf.writeVarInt(1);buf.writeVarInt(18);buf.writeVarInt(5);
            assertThrows(IllegalArgumentException.class,()->CommerceMenu.readOffers(buf));
            buf.clear();buf.writeVarInt(CommerceRules.MAX_OFFERS+1);
            assertThrows(IllegalArgumentException.class,()->CommerceMenu.readOffers(buf));
        }finally{buf.release();}
    }
}
