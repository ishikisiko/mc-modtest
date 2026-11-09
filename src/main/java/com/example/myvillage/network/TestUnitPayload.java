package com.example.myvillage.network;

import com.example.myvillage.item.TestUnitConfig;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** 客户端只提交有限操作与配置；不接受 NBT、命令或生成坐标。 */
public record TestUnitPayload(String action, String data) implements CustomPacketPayload {
    public static final Type<TestUnitPayload> TYPE = new Type<>(ResourceLocation.parse("myvillage:test_unit"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TestUnitPayload> STREAM_CODEC = new StreamCodec<>() {
        public TestUnitPayload decode(RegistryFriendlyByteBuf b) { return new TestUnitPayload(b.readUtf(16), b.readUtf(TestUnitConfig.MAX_JSON)); }
        public void encode(RegistryFriendlyByteBuf b, TestUnitPayload p) { b.writeUtf(p.action, 16); b.writeUtf(p.data, TestUnitConfig.MAX_JSON); }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
