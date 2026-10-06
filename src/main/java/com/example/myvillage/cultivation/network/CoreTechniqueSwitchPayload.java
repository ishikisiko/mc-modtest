package com.example.myvillage.cultivation.network;

import com.example.myvillage.MyVillageMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/**
 * Serverbound request to run another learned core technique (运转此心法). The technique id is its
 * only data; the server checks learned, category and lineage and decides any progress loss.
 */
public record CoreTechniqueSwitchPayload(ResourceLocation techniqueId) implements CustomPacketPayload {
    public static final Type<CoreTechniqueSwitchPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MyVillageMod.MOD_ID, "core_technique_switch"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CoreTechniqueSwitchPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public CoreTechniqueSwitchPayload decode(RegistryFriendlyByteBuf buffer) {
                    return new CoreTechniqueSwitchPayload(buffer.readResourceLocation());
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer, CoreTechniqueSwitchPayload payload) {
                    buffer.writeResourceLocation(payload.techniqueId());
                }
            };

    public CoreTechniqueSwitchPayload {
        Objects.requireNonNull(techniqueId, "techniqueId");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
