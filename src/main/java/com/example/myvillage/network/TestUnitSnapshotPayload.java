package com.example.myvillage.network;

import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** 只向操作玩家发送其附近测试单位和操作回执。 */
public record TestUnitSnapshotPayload(String data) implements CustomPacketPayload {
    public static final int MAX_JSON = 262144;
    public static final Type<TestUnitSnapshotPayload> TYPE = new Type<>(ResourceLocation.parse("myvillage:test_unit_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TestUnitSnapshotPayload> STREAM_CODEC = new StreamCodec<>() {
        public TestUnitSnapshotPayload decode(RegistryFriendlyByteBuf b) { return new TestUnitSnapshotPayload(b.readUtf(MAX_JSON)); }
        public void encode(RegistryFriendlyByteBuf b, TestUnitSnapshotPayload p) { b.writeUtf(p.data, MAX_JSON); }
    };
    private static Consumer<TestUnitSnapshotPayload> receiver = p -> { };
    public static void installReceiver(Consumer<TestUnitSnapshotPayload> next) { receiver = Objects.requireNonNull(next); }
    public static void receive(TestUnitSnapshotPayload p) { receiver.accept(p); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
