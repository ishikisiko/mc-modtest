package com.example.myvillage.sim.runtime.net;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.sim.runtime.player.SectDialogueScenes;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Serverbound: the player chose an option in the sect dialogue. Intent only: which option
 * ({@code kind}: {@link #JOIN}, {@link #LEAVE}, {@link #FAREWELL}, the ids of
 * {@link SectDialogueScenes.Option}), the avatar spoken to and the sect it claims. The server
 * finds the entity again, checks range, dimension, role and sect, and only then asks the ledger.
 * Wire: kind as an unsigned byte (an unknown kind is rejected), entity and sect ids as varints.
 */
public record SectIntentPayload(byte kind, int entityId, int sectId) implements CustomPacketPayload {
    public static final byte JOIN = 0;
    public static final byte LEAVE = 1;
    public static final byte FAREWELL = 2;

    public static final Type<SectIntentPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MyVillageMod.MOD_ID, "sect_intent"));
    public static final StreamCodec<FriendlyByteBuf, SectIntentPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public SectIntentPayload decode(FriendlyByteBuf buffer) {
            int kind = buffer.readUnsignedByte();
            if (kind > FAREWELL) {
                throw new DecoderException("unknown sect intent kind " + kind);
            }
            return new SectIntentPayload((byte) kind, buffer.readVarInt(), buffer.readVarInt());
        }

        @Override
        public void encode(FriendlyByteBuf buffer, SectIntentPayload payload) {
            buffer.writeByte(payload.kind());
            buffer.writeVarInt(payload.entityId());
            buffer.writeVarInt(payload.sectId());
        }
    };

    public SectIntentPayload {
        if (kind < JOIN || kind > FAREWELL) {
            throw new IllegalArgumentException("unknown sect intent kind " + kind);
        }
    }

    public SectIntentPayload(SectDialogueScenes.Option option, int entityId, int sectId) {
        this((byte) option.id(), entityId, sectId);
    }

    public SectDialogueScenes.Option option() {
        return SectDialogueScenes.Option.of(kind);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
