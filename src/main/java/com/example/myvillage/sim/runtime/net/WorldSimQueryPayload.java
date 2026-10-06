package com.example.myvillage.sim.runtime.net;

import com.example.myvillage.MyVillageMod;
import java.util.Objects;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Serverbound: one read-only {@link WorldSimQuery} from the 天下 page. The kind travels as an
 * unsigned byte with fixed network ids (an unknown id is rejected), the id as a varint and the
 * search text bounded by {@link WorldSimQuery#MAX_TEXT}.
 */
public record WorldSimQueryPayload(WorldSimQuery query) implements CustomPacketPayload {
    public static final Type<WorldSimQueryPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MyVillageMod.MOD_ID, "world_sim_query"));
    public static final StreamCodec<FriendlyByteBuf, WorldSimQueryPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public WorldSimQueryPayload decode(FriendlyByteBuf buffer) {
                    WorldSimQuery.Kind kind = kindOf(buffer.readUnsignedByte());
                    int id = buffer.readVarInt();
                    String text = buffer.readUtf(WorldSimQuery.MAX_TEXT);
                    return new WorldSimQueryPayload(new WorldSimQuery(kind, id, text));
                }

                @Override
                public void encode(FriendlyByteBuf buffer, WorldSimQueryPayload payload) {
                    WorldSimQuery query = payload.query();
                    buffer.writeByte(networkId(query.kind()));
                    buffer.writeVarInt(query.id());
                    buffer.writeUtf(query.text(), WorldSimQuery.MAX_TEXT);
                }
            };

    public WorldSimQueryPayload {
        Objects.requireNonNull(query, "query");
    }

    /** The fixed network id of a query kind (never the ordinal). */
    static int networkId(WorldSimQuery.Kind kind) {
        return switch (kind) {
            case OVERVIEW -> 0;
            case SECTS -> 1;
            case SECT -> 2;
            case PERSON_SEARCH -> 3;
            case PERSON -> 4;
            case CHRONICLE -> 5;
            case HERE -> 6;
        };
    }

    static WorldSimQuery.Kind kindOf(int networkId) {
        return switch (networkId) {
            case 0 -> WorldSimQuery.Kind.OVERVIEW;
            case 1 -> WorldSimQuery.Kind.SECTS;
            case 2 -> WorldSimQuery.Kind.SECT;
            case 3 -> WorldSimQuery.Kind.PERSON_SEARCH;
            case 4 -> WorldSimQuery.Kind.PERSON;
            case 5 -> WorldSimQuery.Kind.CHRONICLE;
            case 6 -> WorldSimQuery.Kind.HERE;
            default -> throw new IllegalArgumentException("Unknown world-sim query kind network id " + networkId);
        };
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
