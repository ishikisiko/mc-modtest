package com.example.myvillage.sim.runtime.net;

import com.example.myvillage.MyVillageMod;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Serverbound: the player asked to borrow a technique's manual at a scripture shelf. Intent only:
 * the shelf and the technique's ledger id (a path, at most {@value #MAX_TECHNIQUE_ID} chars). The
 * server finds the shelf again, re-checks range, level, membership and the borrowable list, and
 * only then asks the ledger ({@code WorldSim.recordBorrow}).
 */
public record ScriptureBorrowPayload(BlockPos pos, String techniqueId) implements CustomPacketPayload {
    public static final int MAX_TECHNIQUE_ID = ScriptureHallPayload.MAX_TECHNIQUE_ID;

    public static final Type<ScriptureBorrowPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MyVillageMod.MOD_ID, "scripture_borrow"));
    public static final StreamCodec<FriendlyByteBuf, ScriptureBorrowPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public ScriptureBorrowPayload decode(FriendlyByteBuf buffer) {
            return new ScriptureBorrowPayload(buffer.readBlockPos(), buffer.readUtf(MAX_TECHNIQUE_ID));
        }

        @Override
        public void encode(FriendlyByteBuf buffer, ScriptureBorrowPayload payload) {
            buffer.writeBlockPos(payload.pos());
            buffer.writeUtf(payload.techniqueId(), MAX_TECHNIQUE_ID);
        }
    };

    public ScriptureBorrowPayload {
        Objects.requireNonNull(pos, "pos");
        pos = pos.immutable();
        Objects.requireNonNull(techniqueId, "techniqueId");
        if (techniqueId.length() > MAX_TECHNIQUE_ID) {
            throw new IllegalArgumentException("techniqueId longer than " + MAX_TECHNIQUE_ID + " chars");
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
