package com.example.myvillage.combat.network;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.combat.CombatMode;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public record CombatModeSnapshotPayload(CombatMode mode, long revision)
        implements CustomPacketPayload {
    public static final Type<CombatModeSnapshotPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MyVillageMod.MOD_ID, "combat_mode_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CombatModeSnapshotPayload> STREAM_CODEC =
            StreamCodec.composite(
                    CombatNetworkCodecs.COMBAT_MODE,
                    CombatModeSnapshotPayload::mode,
                    ByteBufCodecs.VAR_LONG,
                    CombatModeSnapshotPayload::revision,
                    CombatModeSnapshotPayload::new);

    public CombatModeSnapshotPayload {
        Objects.requireNonNull(mode, "mode");
        if (revision < 0) {
            throw new IllegalArgumentException("Preference revision must be non-negative");
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
