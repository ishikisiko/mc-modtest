package com.example.myvillage.cultivation.fate;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.buffer.Unpooled;
import java.util.Collections;
import java.util.UUID;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class SpiritBodyPayloadTest {
    @Test void ownerSnapshotAndPublicFormHaveSymmetricBoundedWireFormats() {
        var payload=new SpiritBodyStatusPayload(3,Collections.nCopies(10,0.0),10,.025,40,0,100,60,40,40,75,100);
        var b=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        try { SpiritBodyStatusPayload.STREAM_CODEC.encode(b,payload);assertEquals(payload,SpiritBodyStatusPayload.STREAM_CODEC.decode(b));assertEquals(0,b.readableBytes()); }
        finally{b.release();}
        var form=new PhoenixFormPayload(7,UUID.randomUUID(),true);var wire=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        try{PhoenixFormPayload.STREAM_CODEC.encode(wire,form);assertEquals(form,PhoenixFormPayload.STREAM_CODEC.decode(wire));}finally{wire.release();}
    }
    @Test void invalidStateDoesNotBecomeDisplayOrAuthorityData() {
        assertThrows(IllegalArgumentException.class,()->new SpiritBodyStatusPayload(4,Collections.nCopies(10,0.0),10,.025,40,0,100,60,40,40,75,100));
        assertThrows(IllegalArgumentException.class,()->new SpiritBodyStatusPayload(2,Collections.nCopies(10,0.0),10,.025,40,Double.NaN,100,60,0,0,0,100));
    }
    @Test void serverSavePreservesRemainingEmberAndOwnerSnapshotWithoutOfflineCatchup() {
        var id=UUID.randomUUID();var snapshot=new CompoundTag();snapshot.putUUID("UUID",id);snapshot.putFloat("Health",30);
        var entry=new PhoenixEmbersData.Ember(id,"余烬测试","minecraft:overworld",new PhoenixEmberClock(100,40,30,100,63,0,1.2),snapshot,"minecraft:player_attack",null);
        entry.creditPlayer=UUID.randomUUID();entry.creditName="测试攻击者";entry.creditTicks=42;
        var data=new PhoenixEmbersData();data.embers.put(id,entry);
        var restored=PhoenixEmbersData.load(data.save(new CompoundTag(),RegistryAccess.EMPTY),RegistryAccess.EMPTY);
        assertEquals(entry.clock,restored.embers.get(id).clock);assertEquals(30,restored.embers.get(id).snapshot.getFloat("Health"));
        assertTrue(restored.embers.get(id).offline);assertNull(restored.embers.get(id).player);
        assertEquals(entry.creditPlayer,restored.embers.get(id).creditPlayer);assertEquals(42,restored.embers.get(id).creditTicks);
        var corrupted=data.save(new CompoundTag(),RegistryAccess.EMPTY);corrupted.getCompound("embers").getCompound(id.toString()).getCompound("player").putUUID("UUID",UUID.randomUUID());
        assertThrows(IllegalStateException.class,()->PhoenixEmbersData.load(corrupted,RegistryAccess.EMPTY));
    }
}
