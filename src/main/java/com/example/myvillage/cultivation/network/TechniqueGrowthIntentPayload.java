package com.example.myvillage.cultivation.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.Objects;

/** Action 0 practices; 1/2 choose an authored insight. No client costs or gains are accepted. */
public record TechniqueGrowthIntentPayload(ResourceLocation technique, int action, long revision) implements CustomPacketPayload {
    public static final Type<TechniqueGrowthIntentPayload> TYPE = new Type<>(ResourceLocation.parse("myvillage:technique_growth_intent"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TechniqueGrowthIntentPayload> STREAM_CODEC = new StreamCodec<>() {
        public TechniqueGrowthIntentPayload decode(RegistryFriendlyByteBuf b) {
            return new TechniqueGrowthIntentPayload(b.readResourceLocation(), b.readUnsignedByte(), b.readVarLong());
        }
        public void encode(RegistryFriendlyByteBuf b, TechniqueGrowthIntentPayload p) {
            b.writeResourceLocation(p.technique()); b.writeByte(p.action()); b.writeVarLong(p.revision());
        }
    };
    public TechniqueGrowthIntentPayload {
        Objects.requireNonNull(technique);
        if (action < 0 || action > 2 || revision < 0) throw new IllegalArgumentException("Invalid growth intent");
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
