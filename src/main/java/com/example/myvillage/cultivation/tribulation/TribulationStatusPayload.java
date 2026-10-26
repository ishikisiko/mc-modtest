package com.example.myvillage.cultivation.tribulation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** 仅发给所有者；没有客户端伤害/落点/时钟意图。数据只用于界面与局部粒子。 */
public record TribulationStatusPayload(String attempt, String phase, int mainCompleted, int remaining,
        int preparation, int outsideRemaining, int recovery, int failureStabilityLoss, boolean boundaryWarning, String result,
        double x, double y, double z, double radius, double cloudHeight, int points, int particleInterval,
        List<Mark> marks) implements CustomPacketPayload {
    public record Mark(double x, double y, double z, double radius, boolean main, boolean impact) { }
    public static final Type<TribulationStatusPayload> TYPE = new Type<>(ResourceLocation.parse("myvillage:tribulation_status"));
    private static Consumer<TribulationStatusPayload> receiver = ignored -> { };
    public static void installReceiver(Consumer<TribulationStatusPayload> sink) { receiver = Objects.requireNonNull(sink); }
    public static void receive(TribulationStatusPayload payload) { receiver.accept(payload); }
    public TribulationStatusPayload {
        marks = List.copyOf(marks);
        if (mainCompleted < 0 || mainCompleted > 6 || remaining < 0 || preparation < 0 || recovery < 0 || failureStabilityLoss < 0
                || marks.size() > 32 || points < 12 || points > 96 || particleInterval < 1)
            throw new IllegalArgumentException("雷劫显示数据无效");
    }
    public static final StreamCodec<RegistryFriendlyByteBuf, TribulationStatusPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override public TribulationStatusPayload decode(RegistryFriendlyByteBuf b) {
            String attempt = b.readUtf(36), phase = b.readUtf(24);
            int main = b.readVarInt(), remaining = b.readVarInt(), preparation = b.readVarInt(), outside = b.readVarInt(), recovery = b.readVarInt(), loss = b.readVarInt();
            boolean boundary = b.readBoolean(); String result = b.readUtf(160);
            double x = b.readDouble(), y = b.readDouble(), z = b.readDouble(), radius = b.readDouble(), cloud = b.readDouble();
            int points = b.readVarInt(), interval = b.readVarInt(), count = b.readVarInt();
            if (count < 0 || count > 32) throw new IllegalArgumentException("雷劫落点过多");
            List<Mark> marks = new ArrayList<>();
            for (int i = 0; i < count; i++) marks.add(new Mark(b.readDouble(), b.readDouble(), b.readDouble(), b.readDouble(), b.readBoolean(), b.readBoolean()));
            return new TribulationStatusPayload(attempt, phase, main, remaining, preparation, outside, recovery, loss, boundary, result, x, y, z, radius, cloud, points, interval, marks);
        }
        @Override public void encode(RegistryFriendlyByteBuf b, TribulationStatusPayload p) {
            b.writeUtf(p.attempt, 36); b.writeUtf(p.phase, 24); b.writeVarInt(p.mainCompleted); b.writeVarInt(p.remaining);
            b.writeVarInt(p.preparation); b.writeVarInt(p.outsideRemaining); b.writeVarInt(p.recovery); b.writeVarInt(p.failureStabilityLoss); b.writeBoolean(p.boundaryWarning); b.writeUtf(p.result, 160);
            b.writeDouble(p.x); b.writeDouble(p.y); b.writeDouble(p.z); b.writeDouble(p.radius); b.writeDouble(p.cloudHeight);
            b.writeVarInt(p.points); b.writeVarInt(p.particleInterval); b.writeVarInt(p.marks.size());
            for (var m : p.marks) { b.writeDouble(m.x); b.writeDouble(m.y); b.writeDouble(m.z); b.writeDouble(m.radius); b.writeBoolean(m.main); b.writeBoolean(m.impact); }
        }
    };
    public static TribulationStatusPayload of(TribulationAttempt a, int impact, double px, double py, double pz) {
        List<Mark> marks = new ArrayList<>();
        for (int i = 0; i < a.plan().size(); i++) {
            var rule = a.rules().strikes().get(i);
            if (i != impact && !a.warning(i)) continue;
            var mark = a.plan().get(i);
            marks.add(new Mark(rule.main() ? px : mark.x(), rule.main() ? py : mark.y(), rule.main() ? pz : mark.z(),
                    a.rules().echoRadius(), rule.main(), i == impact));
        }
        return new TribulationStatusPayload(a.id().toString(), a.clock().phase().name(), TribulationManager.mainCompleted(a),
                a.rules().durationTicks() - a.clock().elapsed(), a.clock().preparation(), Math.max(0, a.rules().boundaryGraceTicks() - a.clock().outside()),
                a.clock().recovery(), a.rules().failureStabilityLoss(), a.clock().outside() > 0, a.clock().result(), a.x(), a.y(), a.z(), a.rules().arenaRadius(),
                a.rules().cloudHeight(), a.rules().ringPoints(), a.rules().particleIntervalTicks(), marks);
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
