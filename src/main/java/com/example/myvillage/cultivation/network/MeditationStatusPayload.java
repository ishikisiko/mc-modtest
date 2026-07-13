package com.example.myvillage.cultivation.network;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.cultivation.data.AdvancementKind;
import com.example.myvillage.cultivation.meditation.MeditationState;
import com.example.myvillage.cultivation.meditation.MeditationStatus;
import com.example.myvillage.cultivation.meditation.MeditationStopReason;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.Optional;

public record MeditationStatusPayload(
        MeditationState state,
        int preparationTicksRemaining,
        MeditationStopReason reason,
        Optional<AdvancementKind> advancementKind,
        int advancementDurationTicks,
        int advancementTicksRemaining) implements CustomPacketPayload {
    public static final Type<MeditationStatusPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MyVillageMod.MOD_ID, "meditation_status"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MeditationStatusPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public MeditationStatusPayload decode(RegistryFriendlyByteBuf buffer) {
                    return new MeditationStatusPayload(
                            decodeEnum(buffer.readVarInt(), MeditationState.values(), "meditation state"),
                            buffer.readVarInt(),
                            decodeEnum(buffer.readVarInt(), MeditationStopReason.values(), "meditation reason"),
                            decodeAdvancementKind(buffer.readVarInt()),
                            buffer.readVarInt(),
                            buffer.readVarInt());
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer, MeditationStatusPayload payload) {
                    buffer.writeVarInt(payload.state().ordinal());
                    buffer.writeVarInt(payload.preparationTicksRemaining());
                    buffer.writeVarInt(payload.reason().ordinal());
                    buffer.writeVarInt(payload.advancementKind()
                            .map(kind -> kind.ordinal() + 1)
                            .orElse(0));
                    buffer.writeVarInt(payload.advancementDurationTicks());
                    buffer.writeVarInt(payload.advancementTicksRemaining());
                }
            };

    public MeditationStatusPayload {
        advancementKind = Objects.requireNonNull(advancementKind, "advancementKind");
        new MeditationStatus(
                state,
                preparationTicksRemaining,
                reason,
                advancementKind,
                advancementDurationTicks,
                advancementTicksRemaining);
    }

    public MeditationStatusPayload(
            MeditationState state,
            int preparationTicksRemaining,
            MeditationStopReason reason) {
        this(state, preparationTicksRemaining, reason, Optional.empty(), 0, 0);
    }

    public static MeditationStatusPayload fromStatus(MeditationStatus status) {
        return new MeditationStatusPayload(
                status.state(),
                status.preparationTicksRemaining(),
                status.reason(),
                status.advancementKind(),
                status.advancementDurationTicks(),
                status.advancementTicksRemaining());
    }

    public MeditationStatus status() {
        return new MeditationStatus(
                state,
                preparationTicksRemaining,
                reason,
                advancementKind,
                advancementDurationTicks,
                advancementTicksRemaining);
    }

    private static Optional<AdvancementKind> decodeAdvancementKind(int value) {
        if (value == 0) {
            return Optional.empty();
        }
        return Optional.of(decodeEnum(
                value - 1, AdvancementKind.values(), "advancement kind"));
    }

    private static <T> T decodeEnum(int value, T[] values, String label) {
        if (value < 0 || value >= values.length) {
            throw new IllegalArgumentException("Unknown " + label + " network id " + value);
        }
        return values[value];
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
