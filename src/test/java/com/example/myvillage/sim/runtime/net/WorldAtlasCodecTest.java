package com.example.myvillage.sim.runtime.net;
import com.example.myvillage.worldplan.atlas.WorldAtlas;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class WorldAtlasCodecTest {
    @Test void mapAndNonReadyStatesRoundTripWithNoLeftoverBytes() {
        var ready=new WorldAtlas(WorldAtlas.Status.READY,"a".repeat(64),256,4640,4000,
                List.of(new WorldAtlas.Region("zhongzhou","中州",15,-25)),Base64.getEncoder().encodeToString(new byte[65536]));
        var all=new ArrayList<WorldAtlas>();all.add(ready);
        for(var status:WorldAtlas.Status.values())if(status!=WorldAtlas.Status.READY)all.add(WorldAtlas.state(status));
        for(var a:all){var payload=new WorldSimSnapshotPayload(WorldSimSnapshot.atlas(a));var b=new FriendlyByteBuf(Unpooled.buffer());try{
            WorldSimSnapshotPayload.STREAM_CODEC.encode(b,payload);assertTrue(b.readableBytes()<100000);
            assertEquals(payload,WorldSimSnapshotPayload.STREAM_CODEC.decode(b));assertEquals(0,b.readableBytes());
        }finally{b.release();}}
    }
}
