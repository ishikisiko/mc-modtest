package com.example.myvillage.cultivation.network;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.cultivation.data.AdvancementKind;
import com.example.myvillage.cultivation.meditation.MeditationState;
import com.example.myvillage.cultivation.meditation.MeditationStatus;
import com.example.myvillage.cultivation.meditation.MeditationStopReason;
import com.example.myvillage.cultivation.meditation.StudyProgress;
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
        int advancementTicksRemaining,
        Optional<StudyProgress> study) implements CustomPacketPayload {
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
                            buffer.readVarInt(),
                            decodeStudy(buffer));
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
                    encodeStudy(buffer, payload.study());
                }
            };

    public MeditationStatusPayload {
        advancementKind = Objects.requireNonNull(advancementKind, "advancementKind");
        study = Objects.requireNonNull(study, "study");
        new MeditationStatus(
                state,
                preparationTicksRemaining,
                reason,
                advancementKind,
                advancementDurationTicks,
                advancementTicksRemaining,
                study);
    }

    public MeditationStatusPayload(
            MeditationState state,
            int preparationTicksRemaining,
            MeditationStopReason reason,
            Optional<AdvancementKind> advancementKind,
            int advancementDurationTicks,
            int advancementTicksRemaining) {
        this(state, preparationTicksRemaining, reason, advancementKind,
                advancementDurationTicks, advancementTicksRemaining, Optional.empty());
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
                status.advancementTicksRemaining(),
                status.study());
    }

    public MeditationStatus status() {
        return new MeditationStatus(
                state,
                preparationTicksRemaining,
                reason,
                advancementKind,
                advancementDurationTicks,
                advancementTicksRemaining,
                study);
    }

    /** A presence flag, then technique id, points, total, next gate + 1 (0 = none) and the gate cost. */
    private static void encodeStudy(RegistryFriendlyByteBuf buffer, Optional<StudyProgress> study) {
        buffer.writeBoolean(study.isPresent());
        if (study.isEmpty()) {
            return;
        }
        StudyProgress progress = study.get();
        buffer.writeResourceLocation(progress.techniqueId());
        buffer.writeVarInt(progress.points());
        buffer.writeVarInt(progress.totalPoints());
        buffer.writeVarInt(progress.nextGatePoints() + 1);
        buffer.writeVarInt(progress.gateStabilityCost());
    }

    private static Optional<StudyProgress> decodeStudy(RegistryFriendlyByteBuf buffer) {
        if (!buffer.readBoolean()) {
            return Optional.empty();
        }
        return Optional.of(new StudyProgress(
                buffer.readResourceLocation(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt() - 1,
                buffer.readVarInt()));
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
