package com.example.myvillage.npc;

import static org.junit.jupiter.api.Assertions.*;
import com.example.myvillage.sim.fate.NpcFateRoll;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

class NpcFateSyncTest {
    @Test void preservesBirthOrderBudgetAndUnknownDefinitionWithoutRegistryLookup() {
        var value = new NpcFateRoll(List.of("myvillage:new_trait", "myvillage:bright", "myvillage:robust"), 9, 8);
        var buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            NpcFateSync.write(buf, value);
            assertEquals(value, NpcFateSync.read(buf));
            assertEquals(0, buf.readableBytes());
            buf.clear();
            NpcFateSync.write(buf, null);
            assertNull(NpcFateSync.read(buf));
            assertEquals(0, buf.readableBytes());
        } finally { buf.release(); }
    }
    @Test void rejectsInvalidBirthMetadataAndDuplicateIdsOnDecode() {
        for (int[] values : List.of(new int[]{3,3}, new int[]{19,8}, new int[]{9,10}, new int[]{9,2})) {
            var buf = new FriendlyByteBuf(Unpooled.buffer());
            try {
                buf.writeBoolean(true); buf.writeByte(values[0]); buf.writeByte(values[1]);
                for (int i=0;i<3;i++) buf.writeUtf("myvillage:id_"+i);
                assertThrows(IllegalArgumentException.class, () -> NpcFateSync.read(buf));
            } finally { buf.release(); }
        }
    }
    @Test void rejectsDuplicateIdsEvenWithValidBudget() {
        var buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buf.writeBoolean(true); buf.writeByte(9); buf.writeByte(7);
            for (int i=0;i<3;i++) buf.writeUtf("myvillage:duplicate");
            assertThrows(IllegalArgumentException.class, () -> NpcFateSync.read(buf));
        } finally { buf.release(); }
    }
    @Test void boundsIdLengthBeforeAllocatingUnboundedText() {
        var buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buf.writeBoolean(true); buf.writeByte(9); buf.writeByte(7); buf.writeUtf("myvillage:"+"x".repeat(257));
            assertThrows(DecoderException.class, () -> NpcFateSync.read(buf));
        } finally { buf.release(); }
    }
}
