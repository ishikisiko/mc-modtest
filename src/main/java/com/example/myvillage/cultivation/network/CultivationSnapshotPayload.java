package com.example.myvillage.cultivation.network;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.cultivation.CultivationProfile;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public record CultivationSnapshotPayload(CultivationProfile profile) implements CustomPacketPayload {
    public static final Type<CultivationSnapshotPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MyVillageMod.MOD_ID, "cultivation_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CultivationSnapshotPayload> STREAM_CODEC =
            ByteBufCodecs.fromCodec(CultivationProfile.CODEC)
                    .map(CultivationSnapshotPayload::new, CultivationSnapshotPayload::profile)
                    .cast();

    public CultivationSnapshotPayload {
        Objects.requireNonNull(profile, "profile");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
