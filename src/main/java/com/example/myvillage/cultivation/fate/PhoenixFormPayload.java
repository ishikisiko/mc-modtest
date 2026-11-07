package com.example.myvillage.cultivation.fate;

import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** 旁观者仅接收形态，不接收他人的火种与适应明细。 */
public record PhoenixFormPayload(int entityId,UUID owner,boolean ember) implements CustomPacketPayload {
    public static final Type<PhoenixFormPayload> TYPE=new Type<>(ResourceLocation.parse("myvillage:phoenix_form"));
    private static Consumer<PhoenixFormPayload> receiver=p->{};
    public static void installReceiver(Consumer<PhoenixFormPayload> consumer){receiver=java.util.Objects.requireNonNull(consumer);}
    public static void receive(PhoenixFormPayload payload){receiver.accept(payload);}
    public PhoenixFormPayload { if(entityId<0||owner==null)throw new IllegalArgumentException("非法余烬形态"); }
    public static final StreamCodec<RegistryFriendlyByteBuf,PhoenixFormPayload> STREAM_CODEC=new StreamCodec<>() {
        @Override public PhoenixFormPayload decode(RegistryFriendlyByteBuf b){return new PhoenixFormPayload(b.readVarInt(),b.readUUID(),b.readBoolean());}
        @Override public void encode(RegistryFriendlyByteBuf b,PhoenixFormPayload p){b.writeVarInt(p.entityId);b.writeUUID(p.owner);b.writeBoolean(p.ember);}
    };
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
