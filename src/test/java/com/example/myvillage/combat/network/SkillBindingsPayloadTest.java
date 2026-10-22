package com.example.myvillage.combat.network;
import com.example.myvillage.combat.*;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SkillBindingsPayloadTest {
    private static ResourceLocation id(String s){return ResourceLocation.parse("myvillage:"+s);}
    private static RegistryFriendlyByteBuf buffer(){return new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY,ConnectionType.NEOFORGE);}
    @Test void boundedAssignmentSnapshotAndInputRoundTrip() {
        var b=buffer();try {
            var edit=new SkillBindingPayload(5,id("liuyun_bu"));SkillBindingPayload.STREAM_CODEC.encode(b,edit);assertEquals(edit,SkillBindingPayload.STREAM_CODEC.decode(b));
            var snapshot=new SkillBindingsSnapshotPayload(8,id("dim"),SkillBindings.defaults().with(5,id("liuyun_bu")));
            SkillBindingsSnapshotPayload.STREAM_CODEC.encode(b,snapshot);assertEquals(snapshot,SkillBindingsSnapshotPayload.STREAM_CODEC.decode(b));
            for(var direction:DodgeDirection.values())for(int slot=0;slot<6;slot++){
                var input=new BoundSkillIntentPayload(slot,direction);BoundSkillIntentPayload.STREAM_CODEC.encode(b,input);assertEquals(input,BoundSkillIntentPayload.STREAM_CODEC.decode(b));
            }
            assertEquals(0,b.readableBytes());
        }finally{b.release();}
    }
    @Test void rejectsMalformedWireSlotsIdsAndDirections() {
        var b=buffer();try {
            b.writeByte(7);b.writeResourceLocation(id("jianqi_jue"));assertThrows(IllegalArgumentException.class,()->SkillBindingPayload.STREAM_CODEC.decode(b));b.clear();
            b.writeByte(1);b.writeUtf("myvillage:"+"a".repeat(129));assertThrows(Exception.class,()->SkillBindingPayload.STREAM_CODEC.decode(b));b.clear();
            b.writeByte(6);b.writeByte(0);assertThrows(IllegalArgumentException.class,()->BoundSkillIntentPayload.STREAM_CODEC.decode(b));b.clear();
            b.writeByte(1);b.writeByte(127);assertThrows(IllegalArgumentException.class,()->BoundSkillIntentPayload.STREAM_CODEC.decode(b));
        }finally{b.release();}
        assertThrows(IllegalArgumentException.class,()->new SkillBindingPayload(3,SkillBindings.AUTO));
        assertThrows(IllegalArgumentException.class,()->new SkillBindingPayload(6,id("jianqi_jue")));
    }
}
