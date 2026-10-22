package com.example.myvillage.combat.network;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.example.myvillage.combat.network.CombatHotbarPayload.*;
class CombatHotbarPayloadTest {
    private static ResourceLocation id(String name){return ResourceLocation.parse("myvillage:"+name);}
    @Test void fixedEightSlotsRoundTripEveryStateWithoutAuthorityIntent() {
        for(int state=LOCKED;state<=BLOCKED;state++) {
            var slots=List.of(new Slot(Optional.of(id("qinglian_jiange")),state,true,30,200,0),
                    new Slot(Optional.empty(),LOCKED,false,0,0,0),
                    new Slot(Optional.of(id("jianqi_jue")),CHARGING,false,0,0,100),
                    new Slot(Optional.of(id("liuyun_bu")),COOLDOWN,true,20,60,0),
                    new Slot(Optional.empty(),LOCKED,false,0,0,0),new Slot(Optional.empty(),LOCKED,false,0,0,0),
                    new Slot(Optional.empty(),LOCKED,false,0,0,0),new Slot(Optional.empty(),LOCKED,false,0,0,0));
            var p=new CombatHotbarPayload(42,id("dimension"),id("weapon"),slots);
            var b=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY,ConnectionType.NEOFORGE);
            try {STREAM_CODEC.encode(b,p);assertEquals(p,STREAM_CODEC.decode(b));assertEquals(0,b.readableBytes());}
            finally{b.release();}
        }
    }
    @Test void unboundedAndInvalidSnapshotsAreRejected() {
        assertThrows(IllegalArgumentException.class,()->new Slot(Optional.empty(),6,true,0,0,0));
        assertThrows(IllegalArgumentException.class,()->new Slot(Optional.empty(),READY,true,10,9,0));
        assertThrows(IllegalArgumentException.class,()->new Slot(Optional.empty(),READY,true,0,12001,0));
        assertThrows(IllegalArgumentException.class,()->new Slot(Optional.empty(),READY,true,0,0,101));
        assertThrows(IllegalArgumentException.class,()->new Slot(Optional.of(id("x".repeat(129))),READY,true,0,0,0));
        assertThrows(IllegalArgumentException.class,()->new CombatHotbarPayload(0,id("d"),id("w"),List.of()));
        var slot=new Slot(Optional.empty(),LOCKED,false,0,0,0);
        assertThrows(IllegalArgumentException.class,()->new CombatHotbarPayload(-1,id("d"),id("w"),Collections.nCopies(SLOT_COUNT,slot)));
    }
}
