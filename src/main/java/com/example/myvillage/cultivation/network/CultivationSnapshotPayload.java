package com.example.myvillage.cultivation.network;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.cultivation.CultivationProfile;
import com.example.myvillage.cultivation.fate.FateState;
import com.example.myvillage.cultivation.crafting.CraftingAptitudes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.Objects;

public record CultivationSnapshotPayload(CultivationProfile profile, FateState fates, int fateBudget,
        CraftingAptitudes crafting, int alchemyQualityCap, int forgingQualityCap) implements CustomPacketPayload {
    public static final Type<CultivationSnapshotPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MyVillageMod.MOD_ID, "cultivation_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CultivationSnapshotPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.fromCodec(CultivationProfile.CODEC), CultivationSnapshotPayload::profile,
            ByteBufCodecs.fromCodec(FateState.CODEC), CultivationSnapshotPayload::fates,
            ByteBufCodecs.VAR_INT, CultivationSnapshotPayload::fateBudget,
            ByteBufCodecs.fromCodec(CraftingAptitudes.CODEC), CultivationSnapshotPayload::crafting,
            ByteBufCodecs.VAR_INT, CultivationSnapshotPayload::alchemyQualityCap,
            ByteBufCodecs.VAR_INT, CultivationSnapshotPayload::forgingQualityCap,
            CultivationSnapshotPayload::new).cast();
    public CultivationSnapshotPayload(CultivationProfile profile) { this(profile, FateState.empty(), 8); }
    public CultivationSnapshotPayload(CultivationProfile profile, FateState fates, int fateBudget) {
        this(profile, fates, fateBudget, CraftingAptitudes.EMPTY, 0, 0);
    }
    public CultivationSnapshotPayload {
        Objects.requireNonNull(profile, "profile"); Objects.requireNonNull(fates, "fates");
        Objects.requireNonNull(crafting, "crafting");
        if (fateBudget < 3 || fateBudget > 18) throw new IllegalArgumentException("Invalid fate budget");
        if (crafting.initialized() ? alchemyQualityCap < 1 || alchemyQualityCap > 4 || forgingQualityCap < 1 || forgingQualityCap > 4
                : alchemyQualityCap != 0 || forgingQualityCap != 0)
            throw new IllegalArgumentException("Invalid initialized crafting quality caps");
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
