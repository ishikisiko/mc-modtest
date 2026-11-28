package com.example.myvillage.worldplan.wonder;

import com.example.myvillage.block.ModBlocks;
import com.example.myvillage.entity.beast.FieldEncounterRules;
import com.example.myvillage.worldplan.FieldBiomeLayout;
import com.example.myvillage.worldplan.wonder.FieldEncounterSites.Site;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Positions for reversible terrain overlays. Every write requires free space in a loaded chunk. */
final class FieldArenaTerrain {
    private FieldArenaTerrain() {}

    static List<BlockPos> tide(ServerLevel level, FieldBiomeLayout field, Site site) {
        int sea = field.world().rules().seaLevel;
        var result = new ArrayList<BlockPos>();
        var pos = new BlockPos.MutableBlockPos();
        var box = site.bounds();
        for (int z = box.minZ(); z <= box.maxZ(); z++) for (int x = box.minX(); x <= box.maxX(); x++) {
            int top = field.terrain().tideTop(site.bay(), x, z);
            if (top <= sea) continue;
            pos.set(x, top - 1, z);
            if (!level.hasChunkAt(pos) || !level.getBlockState(pos).is(Blocks.AIR)) continue;
            int floor = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (floor >= top) continue;
            result.add(pos.immutable());
        }
        int budget = FieldEncounterRules.bundled().encounter().maxTideBlocks();
        if (result.size() > budget) {
            BlockPos shoal = site.exposedShoal();
            result.sort(Comparator.comparingDouble(p -> horizontalDistance(p, shoal.getX(), shoal.getZ())));
            result.subList(budget,result.size()).clear();
        }
        var mouth = field.terrain().bayPoint(site.bay(), 0, 104);
        result.sort(Comparator.comparingDouble(p -> horizontalDistance(p, mouth.x(), mouth.z())));
        return result;
    }

    private static double horizontalDistance(BlockPos pos, double x, double z) {
        double dx = pos.getX() - x, dz = pos.getZ() - z;
        return dx*dx + dz*dz;
    }

    static List<BlockPos> bridge(ServerLevel level, Site site) {
        BlockPos a = site.bridgeA(), b = site.bridgeB();
        int halfWidth = FieldEncounterRules.bundled().encounter().bridgeWidth() / 2;
        var openings = new ArrayList<BlockPos>();
        for (int dz = -halfWidth; dz <= halfWidth; dz++) {
            var start = a.offset(0, -1, dz);
            var end = b.offset(0, -1, dz);
            if (!support(level, start) || !support(level, end)) return List.of();
            if (!walkableLanding(level, start) || !walkableLanding(level, end)) return List.of();
            for (int x = a.getX() + 1; x < b.getX(); x++) {
                double fraction = (double)(x - a.getX()) / (b.getX() - a.getX());
                int y = (int)Math.round(start.getY() + (end.getY() - start.getY()) * fraction);
                var p = new BlockPos(x, y, start.getZ());
                if (!level.hasChunkAt(p)) return List.of();
                if (!level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()
                        || !level.getBlockState(p.above(2)).getCollisionShape(level, p.above(2)).isEmpty()) return List.of();
                if (level.getBlockState(p).is(Blocks.AIR)) openings.add(p);
                else if (!support(level, p)) return List.of();
            }
        }
        return openings;
    }

    private static boolean support(ServerLevel level, BlockPos pos) {
        return level.hasChunkAt(pos) && level.getBlockState(pos).isCollisionShapeFullBlock(level, pos)
                && !level.getBlockState(pos).is(ModBlocks.ENCOUNTER_STELE.get());
    }

    private static boolean walkableLanding(ServerLevel level, BlockPos support) {
        BlockPos feet = support.above();
        if (level.getBlockState(feet).isCollisionShapeFullBlock(level, feet)) feet = feet.above();
        return level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty();
    }

    static boolean place(ServerLevel level, BlockPos pos, Block block) {
        if (!level.hasChunkAt(pos) || !level.getBlockState(pos).is(Blocks.AIR)) return false;
        if (!level.setBlock(pos, block.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE)) return false;
        level.scheduleTick(pos, block, 200);
        return true;
    }

    static void remove(ServerLevel level, BlockPos pos, Block block) {
        if (level.hasChunkAt(pos) && level.getBlockState(pos).is(block)) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
    }

    static AABB bridgeWalkway(Site site) {
        double across = FieldEncounterRules.bundled().encounter().bridgeWidth() / 2.0 + 1;
        return AABB.encapsulatingFullBlocks(site.bridgeA(), site.bridgeB()).inflate(1, 2, across);
    }
}
