package com.example.myvillage.cultivation.network;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.cultivation.time.CultivationTimeStatus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record CultivationTimeSnapshotPayload(
        long elapsedCalendarTicks,
        long lifespanConsumedTicks,
        long ticksPerDay,
        int daysPerYear,
        int maximumLifespanYears,
        long maximumLifespanTicks,
        long remainingLifespanTicks,
        boolean exhausted) implements CustomPacketPayload {
    public static final Type<CultivationTimeSnapshotPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MyVillageMod.MOD_ID, "cultivation_time_snapshot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CultivationTimeSnapshotPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public CultivationTimeSnapshotPayload decode(RegistryFriendlyByteBuf buffer) {
                    return new CultivationTimeSnapshotPayload(
                            buffer.readLong(),
                            buffer.readLong(),
                            buffer.readLong(),
                            buffer.readVarInt(),
                            buffer.readVarInt(),
                            buffer.readLong(),
                            buffer.readLong(),
                            buffer.readBoolean());
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer, CultivationTimeSnapshotPayload payload) {
                    buffer.writeLong(payload.elapsedCalendarTicks());
                    buffer.writeLong(payload.lifespanConsumedTicks());
                    buffer.writeLong(payload.ticksPerDay());
                    buffer.writeVarInt(payload.daysPerYear());
                    buffer.writeVarInt(payload.maximumLifespanYears());
                    buffer.writeLong(payload.maximumLifespanTicks());
                    buffer.writeLong(payload.remainingLifespanTicks());
                    buffer.writeBoolean(payload.exhausted());
                }
            };

    public CultivationTimeSnapshotPayload {
        if (elapsedCalendarTicks < 0 || lifespanConsumedTicks < 0 || ticksPerDay <= 0 || daysPerYear <= 0) {
            throw new IllegalArgumentException("Cultivation time counters and scale must be non-negative and positive");
        }
        boolean unavailable = maximumLifespanYears == -1
                && maximumLifespanTicks == -1
                && remainingLifespanTicks == -1;
        boolean available = maximumLifespanYears > 0
                && maximumLifespanTicks > 0
                && remainingLifespanTicks >= 0;
        if (!unavailable && !available) {
            throw new IllegalArgumentException("Maximum and remaining lifespan must be all available or all unavailable");
        }
        if (unavailable && exhausted) {
            throw new IllegalArgumentException("Unavailable lifespan cannot be marked exhausted");
        }
        if (available && (remainingLifespanTicks > maximumLifespanTicks
                || exhausted != (remainingLifespanTicks == 0))) {
            throw new IllegalArgumentException("Available lifespan bounds and exhaustion are inconsistent");
        }
    }

    public boolean lifespanAvailable() {
        return maximumLifespanYears > 0;
    }

    public static CultivationTimeSnapshotPayload fromStatus(CultivationTimeStatus status) {
        if (!status.realmResolved()) {
            return new CultivationTimeSnapshotPayload(
                    status.elapsedCalendarTicks(),
                    status.effectiveLifespanConsumedTicks(),
                    status.ticksPerDay(),
                    status.daysPerYear(),
                    -1,
                    -1,
                    -1,
                    false);
        }
        long ticksPerYear = status.ticksPerYear();
        long maximumYears = status.maximumLifespanTicks() / ticksPerYear;
        if (maximumYears <= 0 || maximumYears > Integer.MAX_VALUE
                || status.maximumLifespanTicks() % ticksPerYear != 0) {
            throw new IllegalArgumentException("Resolved maximum lifespan is not a whole supported year count");
        }
        return new CultivationTimeSnapshotPayload(
                status.elapsedCalendarTicks(),
                status.effectiveLifespanConsumedTicks(),
                status.ticksPerDay(),
                status.daysPerYear(),
                (int) maximumYears,
                status.maximumLifespanTicks(),
                status.remainingLifespanTicks(),
                status.exhausted());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
