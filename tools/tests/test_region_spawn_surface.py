"""直接编译运行生产 SpawnSurfaceSearch；仅用小型世界替身，不启动游戏。

替身复现已核对的 MC 1.21.1 Level.getHeight 边界：未加载列返回
minBuildHeight，getChunk 才加载。方块/高度是测试输入，不复制搜索算法。
"""

import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]
PRODUCTION = ROOT / 'src/main/java/com/example/myvillage/region/runtime/SpawnSurfaceSearch.java'

STUBS = {
    'net/minecraft/core/BlockPos.java': '''
package net.minecraft.core;
public record BlockPos(int x, int y, int z) {}
''',
    'net/minecraft/core/Direction.java': '''
package net.minecraft.core;
public enum Direction { UP }
''',
    'net/minecraft/world/level/levelgen/Heightmap.java': '''
package net.minecraft.world.level.levelgen;
public class Heightmap { public enum Types { WORLD_SURFACE, MOTION_BLOCKING } }
''',
    'net/minecraft/world/level/material/FluidState.java': '''
package net.minecraft.world.level.material;
public record FluidState(boolean isEmpty) {}
''',
    'net/minecraft/world/level/block/state/BlockState.java': '''
package net.minecraft.world.level.block.state;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.material.FluidState;
public record BlockState(boolean isAir, boolean sturdy, boolean liquid) {
    public FluidState getFluidState() { return new FluidState(!liquid); }
    public boolean isFaceSturdy(ServerLevel level, BlockPos pos, Direction face) { return sturdy; }
}
''',
    'net/minecraft/server/level/ServerLevel.java': '''
package net.minecraft.server.level;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
public class ServerLevel {
    public static final BlockState AIR = new BlockState(true, false, false);
    public static final BlockState SOLID = new BlockState(false, true, false);
    public static final BlockState FLUID = new BlockState(false, false, true);
    public static final BlockState FRAGILE = new BlockState(false, false, false);
    public record Column(int top, BlockState surface, BlockState feet, BlockState head) {}
    public final Set<String> loaded = new HashSet<>();
    public final List<String> reads = new ArrayList<>();
    public final Set<String> visited = new HashSet<>();
    public final Map<String, Column> columns = new HashMap<>();
    public final int defaultTop;
    public final BlockState defaultSurface;
    public int unloadedHeightReads;
    public ServerLevel(int top, BlockState surface) { defaultTop = top; defaultSurface = surface; }
    public Object getChunk(int cx, int cz) { loaded.add(cx + "," + cz); return this; }
    public int getMinBuildHeight() { return -64; }
    public void column(int x, int z, int top, BlockState surface, BlockState feet, BlockState head) {
        columns.put(x + "," + z, new Column(top, surface, feet, head));
    }
    private Column at(int x, int z) {
        return columns.getOrDefault(x + "," + z, new Column(defaultTop, defaultSurface, AIR, AIR));
    }
    public int getHeight(Heightmap.Types type, int x, int z) {
        if (reads.size() < 16) reads.add(type + ":" + x + "," + z);
        visited.add(x + "," + z);
        if (!loaded.contains((x >> 4) + "," + (z >> 4))) {
            unloadedHeightReads++;
            return getMinBuildHeight();
        }
        return at(x, z).top();
    }
    public BlockState getBlockState(BlockPos p) {
        if (!loaded.contains((p.x() >> 4) + "," + (p.z() >> 4))) {
            throw new AssertionError("读取方块前未加载区块");
        }
        Column c = at(p.x(), p.z());
        if (p.y() == c.top() - 1) return c.surface();
        if (p.y() == c.top()) return c.feet();
        if (p.y() == c.top() + 1) return c.head();
        return AIR;
    }
}
''',
}

HARNESS = '''
import com.example.myvillage.region.runtime.SpawnSurfaceSearch;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import static net.minecraft.server.level.ServerLevel.*;
public class SpawnSurfaceHarness {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
    private static void position(BlockPos p, int x, int y, int z) {
        check(p.equals(new BlockPos(x, y, z)), "坐标错误: " + p);
    }
    public static void main(String[] args) {
        switch (args[0]) {
            case "unloaded" -> {
                ServerLevel l = new ServerLevel(73, SOLID);
                position(SpawnSurfaceSearch.findStandable(l, 0, 2626).orElseThrow(), 0, 73, 2626);
                check(l.loaded.equals(java.util.Set.of("0,164")), "中心应只加载所在区块");
                check(l.reads.size() == 1 && l.unloadedHeightReads == 0, "首列应读取真实高度");
            }
            case "negative" -> {
                int[] xs = {-1, -16, -17, 15, 16};
                int[] expected = {-1, -1, -2, 0, 1};
                for (int i = 0; i < xs.length; i++) {
                    ServerLevel l = new ServerLevel(81, SOLID);
                    position(SpawnSurfaceSearch.findStandable(l, xs[i], -17).orElseThrow(), xs[i], 81, -17);
                    check(l.loaded.equals(java.util.Set.of(expected[i] + ",-2")), "负坐标应向下取整");
                    check(l.unloadedHeightReads == 0, "不可先读未加载高度");
                }
            }
            case "fallback" -> {
                ServerLevel l = new ServerLevel(96, FLUID);
                position(SpawnSurfaceSearch.heightmapSurface(l, -17, 2626), -17, 96, 2626);
                check(l.loaded.equals(java.util.Set.of("-2,164")), "fallback也须加载");
                check(l.reads.equals(java.util.List.of("WORLD_SURFACE:-17,2626")), "保留原高度图类型");
                check(l.unloadedHeightReads == 0, "fallback不应读取minheight哨兵");
            }
            case "reject" -> {
                for (String reason : new String[]{"surface-fluid", "feet-fluid", "head-fluid", "head-solid", "feet-solid", "fragile"}) {
                    ServerLevel l = new ServerLevel(70, SOLID);
                    l.column(16, 16, 70,
                        reason.equals("surface-fluid") ? FLUID : reason.equals("fragile") ? FRAGILE : SOLID,
                        reason.equals("feet-fluid") ? FLUID : reason.equals("feet-solid") ? SOLID : AIR,
                        reason.equals("head-fluid") ? FLUID : reason.equals("head-solid") ? SOLID : AIR);
                    position(SpawnSurfaceSearch.findStandable(l, 16, 16).orElseThrow(), 15, 70, 15);
                    check(l.loaded.containsAll(java.util.Set.of("1,1", "0,0")), "跨区块候选也须加载");
                    check(l.unloadedHeightReads == 0, reason);
                }
            }
            case "exhausted" -> {
                ServerLevel l = new ServerLevel(63, FLUID);
                check(SpawnSurfaceSearch.findStandable(l, 0, 0).isEmpty(), "全水面不可当可站立点");
                int side = SpawnSurfaceSearch.MAX_RADIUS * 2 + 1;
                check(l.visited.size() == side * side, "搜索应覆盖且仅覆盖既有半径");
                check(l.unloadedHeightReads == 0, "不可跳过未加载候选");
            }
            default -> throw new AssertionError(args[0]);
        }
        System.out.println("PASS " + args[0]);
    }
}
'''


class SpawnSurfaceBehaviorTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if not shutil.which('javac') or not shutil.which('java'):
            raise RuntimeError('出生点行为回归需要javac/java；禁止静默跳过')
        cls.temp = tempfile.TemporaryDirectory(prefix='spawn-surface-')
        cls.folder = Path(cls.temp.name)
        sources = []
        for name, source in STUBS.items():
            path = cls.folder / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(source, encoding='utf-8')
            sources.append(str(path))
        runner = cls.folder / 'SpawnSurfaceHarness.java'
        runner.write_text(HARNESS, encoding='utf-8')
        # 可传修复前原文件做mutation复验；默认始终编译当前真实生产文件。
        production = Path(os.environ.get('SPAWN_SURFACE_SOURCE', PRODUCTION))
        subprocess.run(['javac', '-encoding', 'UTF-8', '-d', str(cls.folder),
                        *sources, str(production), str(runner)], check=True, capture_output=True, text=True)

    @classmethod
    def tearDownClass(cls):
        cls.temp.cleanup()

    def run_case(self, case):
        result = subprocess.run(['java', '-Xmx128m', '-cp', str(self.folder),
                                 'SpawnSurfaceHarness', case], capture_output=True, text=True, timeout=30)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn('PASS ' + case, result.stdout)

    def test_unloaded_column_uses_real_surface(self):
        self.run_case('unloaded')

    def test_negative_and_boundary_chunk_coordinates(self):
        self.run_case('negative')

    def test_fallback_loads_before_world_surface_read(self):
        self.run_case('fallback')

    def test_fluids_obstructions_and_unstable_surface_still_rejected(self):
        self.run_case('reject')

    def test_exhaustion_keeps_existing_search_radius(self):
        self.run_case('exhausted')


if __name__ == '__main__':
    unittest.main()
