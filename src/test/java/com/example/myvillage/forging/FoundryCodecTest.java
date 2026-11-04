package com.example.myvillage.forging;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FoundryCodecTest {
    @Test void weightedWorkHeatAndOriginRoundTripWithoutLoss(){
        var craft=new ForgeCraft(900,7650000,"chitong","thunder",1000);
        assertEquals(craft,ForgeCraft.CODEC.parse(JsonOps.INSTANCE,ForgeCraft.CODEC.encodeStart(JsonOps.INSTANCE,craft).getOrThrow()).getOrThrow());
        var buffer=Unpooled.buffer();try{ForgeCraft.STREAM_CODEC.encode(buffer,craft);assertEquals(craft,ForgeCraft.STREAM_CODEC.decode(buffer));assertFalse(buffer.isReadable());}finally{buffer.release();}
        var heat=new ForgeHeat(1000,3000000000L);buffer=Unpooled.buffer();try{ForgeHeat.STREAM_CODEC.encode(buffer,heat);assertEquals(heat,ForgeHeat.STREAM_CODEC.decode(buffer));}finally{buffer.release();}
        var fire=new FireSeed("earth",700,"minecraft:overworld",net.minecraft.core.BlockPos.asLong(-123,-40,987));
        assertEquals(fire,FireSeed.CODEC.parse(JsonOps.INSTANCE,FireSeed.CODEC.encodeStart(JsonOps.INSTANCE,fire).getOrThrow()).getOrThrow());
    }
    @Test void invalidWorkAndHeatCannotCrossPersistenceOrWireBoundaries(){
        assertThrows(IllegalArgumentException.class,()->new ForgeCraft(1,10001,"wood","",0));
        assertThrows(IllegalArgumentException.class,()->new ForgeCraft(1,1000,"wood","",1));
        assertTrue(ForgeHeat.CODEC.parse(JsonOps.INSTANCE,JsonParser.parseString("{\"amount\":1000,\"time\":-1}")).error().isPresent());
        var buffer=Unpooled.buffer();try{net.minecraft.network.codec.ByteBufCodecs.VAR_INT.encode(buffer,-1);net.minecraft.network.codec.ByteBufCodecs.VAR_LONG.encode(buffer,0L);assertThrows(RuntimeException.class,()->ForgeHeat.STREAM_CODEC.decode(buffer));}finally{buffer.release();}
    }
    @Test void heatDecaysAcrossTransportWithoutBeingResetByAnInspection(){
        var heat=new ForgeHeat(900,1000);assertEquals(900,heat.remaining(1000));assertEquals(890,heat.remaining(1100));
        assertEquals(890,heat.remaining(1100));assertEquals(0,heat.remaining(10000));
        assertEquals(900,heat.amount());assertEquals(1000,heat.time());
    }
}
