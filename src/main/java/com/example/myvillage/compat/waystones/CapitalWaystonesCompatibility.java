package com.example.myvillage.compat.waystones;

import com.example.myvillage.worldplan.capital.CapitalCity;
import com.example.myvillage.worldplan.runtime.WorldPlanRuntime;
import net.blay09.mods.balm.api.Balm;
import net.blay09.mods.waystones.api.WaystoneOrigin;
import net.blay09.mods.waystones.api.event.GenerateWaystoneNameEvent;
import net.blay09.mods.waystones.block.WaystoneBlock;
import net.blay09.mods.waystones.block.WaystoneBlockBase;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/** 只在已安装 Waystones 时加载；生成线程只写本区块，不修改全服石碑数据库。 */
public final class CapitalWaystonesCompatibility {
    private CapitalWaystonesCompatibility() { }

    public static void register() {
        Balm.getEvents().onEvent(GenerateWaystoneNameEvent.class, CapitalWaystonesCompatibility::name);
    }

    public static void place(CapitalCity city, WorldGenLevel level, ChunkAccess chunk) {
        int minX = chunk.getPos().getMinBlockX(), minZ = chunk.getPos().getMinBlockZ();
        for (var station : city.bundle().waystones) {
            if (!station.inSlice(minX-city.x(), minZ-city.z())) continue;
            var at = new BlockPos(city.x()+station.x(), city.originY()+station.y(), city.z()+station.z());
            var id = ResourceLocation.parse(station.block());
            if (!BuiltInRegistries.BLOCK.containsKey(id)
                    || !(BuiltInRegistries.BLOCK.get(id) instanceof WaystoneBlock block)) {
                throw new IllegalStateException("已安装的 Waystones 缺少石碑方块 " + id);
            }
            if (!chunk.getBlockState(at).isAir() || !chunk.getBlockState(at.above()).isAir()
                    || chunk.getBlockState(at.below()).isAir()
                    || !chunk.getBlockState(at.below()).getFluidState().isEmpty()) {
                throw new IllegalStateException("主城传送石碑支撑或净空不符 " + station.id() + " " + at);
            }
            var base = block.defaultBlockState().setValue(WaystoneBlockBase.ORIGIN, WaystoneOrigin.VILLAGE)
                    .setValue(WaystoneBlockBase.FACING, Direction.byName(station.facing()));
            for (var half : DoubleBlockHalf.values()) {
                var pos = half == DoubleBlockHalf.LOWER ? at : at.above();
                chunk.setBlockState(pos, base.setValue(WaystoneBlockBase.HALF, half), false);
                // 原生 BE 在区块完成并加载时初始化一次；上半块复用下半块身份。
                var data = new CompoundTag();
                data.putString("id", "DUMMY");
                data.putInt("x", pos.getX()); data.putInt("y", pos.getY()); data.putInt("z", pos.getZ());
                chunk.setBlockEntityNbt(data);
            }
        }
    }

    private static void name(GenerateWaystoneNameEvent event) {
        var waystone = event.getWaystone();
        if (waystone.getOrigin() != WaystoneOrigin.VILLAGE) return;
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        var level = server.getLevel(waystone.getDimension());
        if (level == null) return;
        var generator = WorldPlanRuntime.generator(level).orElse(null);
        if (generator == null) return;
        var pos = waystone.getPos();
        var city = generator.plan().capitalAt(pos.getX(), pos.getZ()).orElse(null);
        if (city == null) return;
        for (var station : city.bundle().waystones) {
            if (pos.getX() == city.x()+station.x() && pos.getY() == city.originY()+station.y()
                    && pos.getZ() == city.z()+station.z()) {
                event.setName(Component.literal(station.name()));
                return;
            }
        }
    }
}
