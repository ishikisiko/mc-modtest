package com.example.myvillage.sim.runtime.net;
import com.example.myvillage.worldplan.atlas.WorldAtlas;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class ProvinceCapitalV8AtlasCodecTest {
 @Test void eightRegionsAndMarkersRoundTripWithoutExtraBytes(){
  var ids=List.of("zhongzhou","donghai","lingyue","ximo","nanjiang","beiyuan","donghuang","moyu");
  var regions=ids.stream().map(x->new WorldAtlas.Region(x,x,0,0)).toList();var cities=ids.stream().map(x->new WorldAtlas.City("capital_"+x,x,x,0,64,0)).toList();
  var atlas=new WorldAtlas(WorldAtlas.Status.READY,"a".repeat(64),256,10000,9000,regions,cities,Base64.getEncoder().encodeToString(new byte[65536]));
  var payload=new WorldSimSnapshotPayload(WorldSimSnapshot.atlas(atlas));var buffer=new FriendlyByteBuf(Unpooled.buffer());try {WorldSimSnapshotPayload.STREAM_CODEC.encode(buffer,payload);assertTrue(buffer.readableBytes()<100000);assertEquals(payload,WorldSimSnapshotPayload.STREAM_CODEC.decode(buffer));assertEquals(0,buffer.readableBytes());}finally{buffer.release();}
  var duplicated=new ArrayList<>(cities);duplicated.add(cities.getFirst());assertThrows(IllegalArgumentException.class,()->new WorldAtlas(WorldAtlas.Status.READY,"a".repeat(64),256,10000,9000,regions,duplicated,atlas.raster()));
 }
}
