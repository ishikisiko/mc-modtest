package com.example.myvillage.talisman;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** 玩家脚下的精确位置；不从客户端接收坐标。 */
public record TravelPoint(ResourceLocation dimension, double x, double y, double z, float yaw, float pitch) {
    private static final Codec<TravelPoint> RAW = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("dimension").forGetter(TravelPoint::dimension),
            Codec.DOUBLE.fieldOf("x").forGetter(TravelPoint::x),
            Codec.DOUBLE.fieldOf("y").forGetter(TravelPoint::y),
            Codec.DOUBLE.fieldOf("z").forGetter(TravelPoint::z),
            Codec.FLOAT.fieldOf("yaw").forGetter(TravelPoint::yaw),
            Codec.FLOAT.fieldOf("pitch").forGetter(TravelPoint::pitch)
    ).apply(i, TravelPoint::new));
    public static final Codec<TravelPoint> CODEC = RAW.validate(p -> p.valid()
            ? DataResult.success(p) : DataResult.error(() -> "闪回地点数据无效"));

    public boolean valid() {
        return dimension != null && Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)
                && Math.abs(x) < 30_000_000 && Math.abs(z) < 30_000_000 && y >= -2048 && y <= 2048
                && Float.isFinite(yaw) && Float.isFinite(pitch) && Math.abs(pitch) <= 90;
    }
    public Vec3 position() { return new Vec3(x, y, z); }
    public static TravelPoint at(ServerPlayer player) {
        return new TravelPoint(player.level().dimension().location(), player.getX(), player.getY(), player.getZ(),
                player.getYRot(), player.getXRot());
    }
}
