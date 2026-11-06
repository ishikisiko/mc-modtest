package com.example.myvillage.cultivation.tribulation;
import static org.junit.jupiter.api.Assertions.*;
import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;
class TribulationPayloadTest {
    private static TribulationStatusPayload payload(int kind,int required,int hits){
        return new TribulationStatusPayload("11111111-2222-3333-4444-555555555555","ACTIVE",kind,required,hits,500,0,100,0,210,false,"渡劫中",10,64,-20,12,12,48,4,
                List.of(new TribulationStatusPayload.Mark(10,64,-20,2.5,true,true)));
    }
    @Test void allKindsAndTwelveHitReadoutRoundTrip(){
        for(var kind:TribulationKind.values()){
            var p=payload(kind.networkId(),12,12);var b=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY,ConnectionType.NEOFORGE);
            try{TribulationStatusPayload.STREAM_CODEC.encode(b,p);assertEquals(p,TribulationStatusPayload.STREAM_CODEC.decode(b));assertEquals(0,b.readableBytes());}finally{b.release();}
        }
    }
    @Test void boundedOwnerReadoutRejectsUnknownKindAndImpossibleCounts(){
        assertThrows(IllegalArgumentException.class,()->payload(4,6,0));assertThrows(IllegalArgumentException.class,()->payload(0,6,7));
        assertThrows(IllegalArgumentException.class,()->payload(0,0,0));assertThrows(IllegalArgumentException.class,()->payload(3,33,0));
    }
}
