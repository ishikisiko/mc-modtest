package com.example.myvillage.sim.runtime.avatar;

import com.example.myvillage.block.ModBlocks;
import com.example.myvillage.block.ScriptureShelfBlock;
import com.example.myvillage.block.entity.ScriptureShelfBlockEntity;
import com.example.myvillage.sect.SectCourtyard;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The scripture shelves (经架) of a ledger sect's compound: one on the hall floor of each scripture
 * pavilion, owned by the sect (its {@link ScriptureShelfBlockEntity} holds the sect id). Placed after
 * a compound is built, by {@link GateBuilder#build} and by {@link GateRealizer} before it releases
 * its chunk tickets; anonymous worldgen compounds get none.
 *
 * <p>The sites are {@link SectCourtyard#scriptureShelfSites} (the pavilion's centre column at the
 * terrace elevation). The pavilion's own floor is higher, so each column is scanned upward from
 * {@code site.y - }{@value #SCAN_BELOW} to {@code site.y + }{@value #SCAN_ABOVE} for the first block
 * with a sturdy top face and two free blocks above it ({@link #floorAbove}); the shelf goes on it. A
 * shelf already there (a rebuild, a replay) counts as free while scanning and only has its sect id
 * refreshed.
 */
public final class ScriptureShelves {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScriptureShelves.class);
    /** Scan start below the site (the terrace floor itself). */
    public static final int SCAN_BELOW = 1;
    /** Highest floor y above the site the scan accepts. */
    public static final int SCAN_ABOVE = 12;
    /** {@link #floorAbove} found no floor. */
    public static final int NONE = Integer.MIN_VALUE;

    private ScriptureShelves() {
    }

    /** One site and what stands there: the shelf position (null when missing) and its sect id. */
    public record Survey(BlockPos site, BlockPos shelf, int sectId) {
    }

    /**
     * Places (or refreshes) the shelves of sect {@code sectId}'s compound built at {@code anchor}
     * with {@code seed} and {@code variant}. Returns the positions that hold the sect's shelf
     * afterwards, in site order; a site without a floor is skipped with a warning.
     */
    public static List<BlockPos> place(ServerLevel level, int sectId, long seed, BlockPos anchor, String variant) {
        List<BlockPos> sites = SectCourtyard.scriptureShelfSites(seed, anchor, variant);
        List<BlockPos> placed = new ArrayList<>();
        for (BlockPos site : sites) {
            int floor = floorAt(level, site);
            if (floor == NONE) {
                LOGGER.warn("SCRIPTURE_SHELF sect={} no floor above {} {} {} (scanned y {}..{}); skipped", sectId,
                        site.getX(), site.getY(), site.getZ(), site.getY() - SCAN_BELOW, site.getY() + SCAN_ABOVE);
                continue;
            }
            BlockPos pos = new BlockPos(site.getX(), floor + 1, site.getZ());
            if (!isShelf(level.getBlockState(pos))) {
                level.setBlock(pos, ModBlocks.SCRIPTURE_SHELF.get().defaultBlockState(), 3);
            }
            BlockEntity entity = level.getBlockEntity(pos);
            if (entity instanceof ScriptureShelfBlockEntity shelf) {
                shelf.setSectId(sectId);
                placed.add(pos);
            } else {
                LOGGER.warn("SCRIPTURE_SHELF sect={} no shelf block entity at {} {} {}; skipped", sectId,
                        pos.getX(), pos.getY(), pos.getZ());
            }
        }
        LOGGER.info("SCRIPTURE_SHELF sect={} placed={}/{} at={}", sectId, placed.size(), sites.size(),
                placed.stream().map(p -> p.getX() + " " + p.getY() + " " + p.getZ())
                        .collect(Collectors.joining(";")));
        return List.copyOf(placed);
    }

    /** What stands at each shelf site of the compound: the first shelf in the scanned column, if any. */
    public static List<Survey> survey(ServerLevel level, long seed, BlockPos anchor, String variant) {
        List<Survey> out = new ArrayList<>();
        for (BlockPos site : SectCourtyard.scriptureShelfSites(seed, anchor, variant)) {
            BlockPos found = null;
            int sectId = -1;
            for (int y = site.getY() - SCAN_BELOW + 1; y <= site.getY() + SCAN_ABOVE + 1 && found == null; y++) {
                BlockPos pos = new BlockPos(site.getX(), y, site.getZ());
                if (isShelf(level.getBlockState(pos))) {
                    found = pos;
                    if (level.getBlockEntity(pos) instanceof ScriptureShelfBlockEntity shelf) {
                        sectId = shelf.sectId();
                    }
                }
            }
            out.add(new Survey(site, found, sectId));
        }
        return List.copyOf(out);
    }

    /** The floor y in the world column of {@code site} ({@link #floorAbove} over the scan range). */
    static int floorAt(BlockGetter level, BlockPos site) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int x = site.getX();
        int z = site.getZ();
        return floorAbove(
                y -> standable(level.getBlockState(cursor.set(x, y, z)), level, cursor.set(x, y, z)),
                y -> free(level.getBlockState(cursor.set(x, y, z))),
                site.getY() - SCAN_BELOW, site.getY() + SCAN_ABOVE);
    }

    /**
     * The first {@code y} in {@code from..to} (inclusive, upward) with {@code solidAt(y)} and
     * {@code airAt(y + 1)} and {@code airAt(y + 2)}; {@link #NONE} if there is none.
     */
    public static int floorAbove(IntPredicate solidAt, IntPredicate airAt, int from, int to) {
        for (int y = from; y <= to; y++) {
            if (solidAt.test(y) && airAt.test(y + 1) && airAt.test(y + 2)) {
                return y;
            }
        }
        return NONE;
    }

    /** A shelf can stand on it: a sturdy top face, and not a shelf itself. */
    public static boolean standable(BlockState state, BlockGetter level, BlockPos pos) {
        return !isShelf(state) && state.isFaceSturdy(level, pos, Direction.UP);
    }

    /** Free for the shelf and the reader above it: air, or a shelf already standing there. */
    public static boolean free(BlockState state) {
        return state.isAir() || isShelf(state);
    }

    /** Class check rather than the registry holder, so it also works where blocks are not registered. */
    public static boolean isShelf(BlockState state) {
        return state.getBlock() instanceof ScriptureShelfBlock;
    }
}
