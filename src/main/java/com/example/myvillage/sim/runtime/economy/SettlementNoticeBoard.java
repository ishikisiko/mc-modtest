package com.example.myvillage.sim.runtime.economy;

import com.example.myvillage.block.*;
import com.example.myvillage.regiongen.BayTerracesLayout;
import com.example.myvillage.sim.life.*;
import com.example.myvillage.sim.runtime.WorldSimRuntime;
import com.example.myvillage.worldplan.runtime.WorldPlanRuntime;
import com.google.gson.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** 小型聚落共用作者点位，跟随原聚落的旋转和垂直偏移，只在自己的生成切片放置。 */
public final class SettlementNoticeBoard {
    private record Definition(String layout, BlockPos root, BlockPos reader, Direction facing) { }
    private static final class Current { static final Definition VALUE = load(); }
    private SettlementNoticeBoard() { }
    private static Definition load() {
        try (var stream = SettlementNoticeBoard.class.getResourceAsStream("/data/myvillage/regiongen/settlement_board.json")) {
            if (stream == null) throw new IllegalArgumentException("缺少聚落江湖榜输入");
            var o = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            if (o.get("schema").getAsInt() != 1) throw new IllegalArgumentException("江湖榜schema无效");
            var root = position(o.getAsJsonArray("position")); var reader = position(o.getAsJsonArray("reader"));
            var facing = Direction.byName(o.get("facing").getAsString());
            if (facing == null || facing.getAxis().isVertical() || root.distManhattan(reader) != 1 || root.getY() != reader.getY()) throw new IllegalArgumentException("江湖榜方向或操作位置无效");
            return new Definition(o.get("layout").getAsString(), root, reader, facing);
        } catch (java.io.IOException error) { throw new IllegalStateException(error); }
    }
    private static BlockPos position(JsonArray a) {
        if (a.size() != 3) throw new IllegalArgumentException("江湖榜坐标无效");
        int x = a.get(0).getAsBigDecimal().intValueExact(), y = a.get(1).getAsBigDecimal().intValueExact(), z = a.get(2).getAsBigDecimal().intValueExact();
        if (x < 0 || x >= 224 || z < 0 || z >= 224 || y < 1 || y > 200) throw new IllegalArgumentException("江湖榜坐标越界");
        return new BlockPos(x, y, z);
    }
    public static BlockPos transform(BlockPos origin, int rotation, boolean reader) {
        var layout = BayTerracesLayout.current(); var d = Current.VALUE;
        if (!layout.id.equals(d.layout())) throw new IllegalStateException("聚落江湖榜布局不符");
        var p = reader ? d.reader() : d.root(); var xz = layout.rotate(p.getX(), p.getZ(), rotation);
        return origin.offset(xz.x(), p.getY(), xz.z());
    }
    public static void place(WorldGenLevel level, BoundingBox clip, BlockPos origin, int rotation) {
        var root = transform(origin, rotation, false); if (!clip.isInside(root)) return;
        var reader = transform(origin, rotation, true);
        if (!clip.isInside(reader) || !level.getBlockState(root).isAir() || !level.getBlockState(reader).isAir()
                || !level.getBlockState(root.below()).isFaceSturdy(level, root.below(), Direction.UP)
                || !level.getBlockState(reader.below()).isFaceSturdy(level, reader.below(), Direction.UP))
            throw new IllegalStateException("聚落江湖榜被占用或缺少真实地面：" + root);
        Direction face = Current.VALUE.facing(); for (int i = 0; i < rotation; i++) face = face.getClockWise();
        level.setBlock(root, ModBlocks.JIANGHU_BOARD.get().defaultBlockState().setValue(EconomyCounterBlock.FACING, face), 18);
    }
    public static boolean bindAt(ServerPlayer player, BlockPos root) {
        var sim = WorldSimRuntime.sim().orElse(null); var generator = WorldPlanRuntime.generator(player.serverLevel()).orElse(null);
        if (sim == null || generator == null || !player.serverLevel().isLoaded(root) || !player.serverLevel().getBlockState(root).is(ModBlocks.JIANGHU_BOARD.get())) return false;
        for (var site : generator.plan().settlements()) {
            var origin = new BlockPos(site.x(), site.offsetY(), site.z());
            if (!transform(origin, site.rotation(), false).equals(root)) continue;
            var reader = transform(origin, site.rotation(), true);
            var place = new LifePlace("jianghu_board", "entry", reader.getX(), reader.getY(), reader.getZ(), 1,
                    "myvillage:jianghu_board", root.getX(), root.getY(), root.getZ());
            var binding = new LifeSite(site.id(), "湾口台地集", "minecraft:overworld", site.host(), root.getX(), root.getY(), root.getZ(), List.of(place));
            if (sim.lifeSite(site.id()).isEmpty()) { sim.registerLifeSite(binding); EconomyRuntime.dirty(player); }
            return sim.lifeSite(site.id()).filter(binding::equals).isPresent();
        }
        return false;
    }
}
