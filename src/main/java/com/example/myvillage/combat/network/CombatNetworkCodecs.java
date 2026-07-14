package com.example.myvillage.combat.network;

import com.example.myvillage.combat.CombatMode;
import com.example.myvillage.combat.session.CombatStopReason;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

final class CombatNetworkCodecs {
    static final StreamCodec<ByteBuf, CombatMode> COMBAT_MODE = enumCodec(CombatMode.values(), "combat mode");
    static final StreamCodec<ByteBuf, CombatStopReason> STOP_REASON =
            enumCodec(CombatStopReason.values(), "combat stop reason");

    private CombatNetworkCodecs() {
    }

    private static <E extends Enum<E>> StreamCodec<ByteBuf, E> enumCodec(E[] values, String label) {
        return StreamCodec.of(
                (buffer, value) -> buffer.writeByte(value.ordinal()),
                buffer -> {
                    int id = buffer.readUnsignedByte();
                    if (id >= values.length) {
                        throw new IllegalArgumentException("Unknown " + label + " id: " + id);
                    }
                    return values[id];
                });
    }
}
