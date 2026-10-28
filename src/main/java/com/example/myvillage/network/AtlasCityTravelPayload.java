package com.example.myvillage.network;

import com.example.myvillage.worldplan.travel.AtlasCityTravelTarget;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Bounded intent only: a frozen city ID and map fingerprint. No XYZ, player ID or permission flags. */
public record AtlasCityTravelPayload(String cityId, String atlasFingerprint) implements CustomPacketPayload {
    public static final Type<AtlasCityTravelPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("myvillage","atlas_city_travel"));
    public static final StreamCodec<FriendlyByteBuf,AtlasCityTravelPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override public AtlasCityTravelPayload decode(FriendlyByteBuf buffer) {
            return new AtlasCityTravelPayload(buffer.readUtf(AtlasCityTravelTarget.MAX_CITY_ID),
                    buffer.readUtf(AtlasCityTravelTarget.FINGERPRINT_LENGTH));
        }
        @Override public void encode(FriendlyByteBuf buffer, AtlasCityTravelPayload payload) {
            buffer.writeUtf(payload.cityId(),AtlasCityTravelTarget.MAX_CITY_ID);
            buffer.writeUtf(payload.atlasFingerprint(),AtlasCityTravelTarget.FINGERPRINT_LENGTH);
        }
    };
    public AtlasCityTravelPayload {
        if (!AtlasCityTravelTarget.validCityId(cityId) || !AtlasCityTravelTarget.validFingerprint(atlasFingerprint))
            throw new IllegalArgumentException("非法舆图传送请求");
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
