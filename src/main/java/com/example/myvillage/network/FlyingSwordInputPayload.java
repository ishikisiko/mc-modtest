package com.example.myvillage.network;

import com.example.myvillage.MyVillageMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record FlyingSwordInputPayload(byte flags) implements CustomPacketPayload {
    public static final int FORWARD = FlyingSwordInputFlags.FORWARD;
    public static final int BACKWARD = FlyingSwordInputFlags.BACKWARD;
    public static final int LEFT = FlyingSwordInputFlags.LEFT;
    public static final int RIGHT = FlyingSwordInputFlags.RIGHT;
    public static final int ASCEND = FlyingSwordInputFlags.ASCEND;
    public static final int DESCEND = FlyingSwordInputFlags.DESCEND;
    public static final int ALL_FLAGS = FlyingSwordInputFlags.ALL;

    public static final Type<FlyingSwordInputPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MyVillageMod.MOD_ID, "flying_sword_input"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FlyingSwordInputPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BYTE,
                    FlyingSwordInputPayload::flags,
                    FlyingSwordInputPayload::new);

    public static FlyingSwordInputPayload fromKeys(
            boolean forward,
            boolean backward,
            boolean left,
            boolean right,
            boolean ascend,
            boolean descend) {
        return new FlyingSwordInputPayload(FlyingSwordInputFlags.pack(
                forward, backward, left, right, ascend, descend));
    }

    public boolean hasOnlyKnownFlags() {
        return FlyingSwordInputFlags.hasOnlyKnownFlags(flags);
    }

    public boolean has(int flag) {
        return FlyingSwordInputFlags.has(flags, flag);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
