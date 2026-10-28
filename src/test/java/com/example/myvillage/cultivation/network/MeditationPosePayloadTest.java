package com.example.myvillage.cultivation.network;

import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MeditationPosePayloadTest {
    @Test void roundTripPreservesObserverIdentityAndEpoch() {
        var p=new MeditationPosePayload(17,UUID.randomUUID(),ResourceLocation.parse("minecraft:overworld"),9,12345,40,true,false);
        var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),
                net.minecraft.core.RegistryAccess.EMPTY,net.neoforged.neoforge.network.connection.ConnectionType.NEOFORGE);
        try {
            MeditationPosePayload.STREAM_CODEC.encode(buffer,p);
            assertEquals(p,MeditationPosePayload.STREAM_CODEC.decode(buffer));
            assertEquals(0,buffer.readableBytes());
        } finally {buffer.release();}
    }
    @Test void malformedSnapshotsFailAtTheBoundary() {
        var dimension=ResourceLocation.parse("minecraft:overworld");var id=UUID.randomUUID();
        assertThrows(IllegalArgumentException.class,()->new MeditationPosePayload(-1,id,dimension,1,0,40,true,false));
        assertThrows(IllegalArgumentException.class,()->new MeditationPosePayload(1,id,dimension,0,0,40,true,false));
        assertThrows(IllegalArgumentException.class,()->new MeditationPosePayload(1,id,dimension,1,0,0,true,false));
        assertThrows(IllegalArgumentException.class,()->new MeditationPosePayload(1,id,dimension,1,0,40,true,true));
    }
    @Test void interruptionReasonsNeverPlayASlowRiseOverCombatOrDeath() {
        for(var reason:new com.example.myvillage.cultivation.meditation.MeditationStopReason[]{
                com.example.myvillage.cultivation.meditation.MeditationStopReason.MOVED,
                com.example.myvillage.cultivation.meditation.MeditationStopReason.DAMAGED,
                com.example.myvillage.cultivation.meditation.MeditationStopReason.ATTACKED,
                com.example.myvillage.cultivation.meditation.MeditationStopReason.DIED,
                com.example.myvillage.cultivation.meditation.MeditationStopReason.DIMENSION_CHANGED})
            assertFalse(com.example.myvillage.cultivation.meditation.MeditationVisuals.graceful(reason));
    }
}
