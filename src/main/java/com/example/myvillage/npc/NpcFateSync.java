package com.example.myvillage.npc;

import com.example.myvillage.sim.fate.NpcFateRoll;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;

/** 只同步出生词条和预算；运行冷却、凝聚状态留在服务器。 */
public final class NpcFateSync {
    private NpcFateSync() { }

    public static void write(FriendlyByteBuf buf, NpcFateRoll fates) {
        buf.writeBoolean(fates != null);
        if (fates == null) return;
        buf.writeByte(fates.budget());
        buf.writeByte(fates.spent());
        for (String id : fates.selected()) buf.writeUtf(id, 256);
    }

    public static NpcFateRoll read(FriendlyByteBuf buf) {
        if (!buf.readBoolean()) return null;
        int budget = buf.readUnsignedByte(), spent = buf.readUnsignedByte();
        return new NpcFateRoll(List.of(buf.readUtf(256), buf.readUtf(256), buf.readUtf(256)), budget, spent);
    }
}
