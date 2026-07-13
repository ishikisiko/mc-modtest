package com.example.myvillage.cultivation.network;

import com.example.myvillage.MyVillageMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public record MeditationIntentPayload(MeditationIntentAction action) implements CustomPacketPayload {
    public static final Type<MeditationIntentPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MyVillageMod.MOD_ID, "meditation_intent"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MeditationIntentPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public MeditationIntentPayload decode(RegistryFriendlyByteBuf buffer) {
                    int networkId = buffer.readUnsignedByte();
                    MeditationIntentAction action = switch (networkId) {
                        case 0 -> MeditationIntentAction.START_NORMAL;
                        case 1 -> MeditationIntentAction.START_SPIRIT;
                        case 2 -> MeditationIntentAction.STOP;
                        case 3 -> MeditationIntentAction.START_BREAKTHROUGH;
                        default -> throw new IllegalArgumentException(
                                "Unknown meditation intent network id " + networkId);
                    };
                    return new MeditationIntentPayload(action);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer, MeditationIntentPayload payload) {
                    buffer.writeByte(switch (payload.action()) {
                        case START_NORMAL -> 0;
                        case START_SPIRIT -> 1;
                        case STOP -> 2;
                        case START_BREAKTHROUGH -> 3;
                    });
                }
            };

    public MeditationIntentPayload {
        Objects.requireNonNull(action, "action");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
