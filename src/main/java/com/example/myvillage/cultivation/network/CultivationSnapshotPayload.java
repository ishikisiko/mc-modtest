package com.example.myvillage.cultivation.network;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.cultivation.CultivationProfile;
import com.example.myvillage.cultivation.fate.FateState;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.Objects;

public record CultivationSnapshotPayload(CultivationProfile profile, FateState fates, int fateBudget) implements CustomPacketPayload {
    public static final Type<CultivationSnapshotPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MyVillageMod.MOD_ID, "cultivation_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CultivationSnapshotPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.fromCodec(CultivationProfile.CODEC), CultivationSnapshotPayload::profile,
            ByteBufCodecs.fromCodec(FateState.CODEC), CultivationSnapshotPayload::fates,
            ByteBufCodecs.VAR_INT, CultivationSnapshotPayload::fateBudget, CultivationSnapshotPayload::new).cast();
    public CultivationSnapshotPayload(CultivationProfile profile) { this(profile, FateState.empty(), 8); }
    public CultivationSnapshotPayload {
        Objects.requireNonNull(profile, "profile"); Objects.requireNonNull(fates, "fates");
        if (fateBudget < 3 || fateBudget > 18) throw new IllegalArgumentException("Invalid fate budget");
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
