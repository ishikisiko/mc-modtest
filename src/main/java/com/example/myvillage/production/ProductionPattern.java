package com.example.myvillage.production;

import com.example.myvillage.gathering.GatheringBlock;
import com.example.myvillage.gathering.GatheringPattern;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

final class ProductionPattern {
    enum Result { OK, UNLOADED, ANCHOR, ORIGINAL_ARRAY, PART, SUPPORT, SHARED, BORDER }

    record Check(Result result, BlockPos anchor, BlockPos issue, ProductionPart expected) {
        boolean formed() { return result == Result.OK; }
    }

    record Rotated(ProductionRules.Cell controller, ProductionRules.Cell outlet, List<ProductionRules.Cell> cells) { }

    private static final class Layouts {
        private static final Map<ProductionKind, Map<Direction, Rotated>> ALL = build();
    }

    static Rotated layout(ProductionKind kind, Direction facing) { return Layouts.ALL.get(kind).get(facing); }

    static Check check(Level level, BlockPos controller, ProductionKind kind, Direction facing) {
        var layout = layout(kind, facing);
        var anchor = controller.subtract(layout.controller.offset());
        if (!level.hasChunkAt(anchor)) return issue(Result.UNLOADED, anchor, anchor, null);
        var anchorBlock = level.getBlockState(anchor).getBlock();
        if (kind.mining()) {
            if (!(anchorBlock instanceof ProductionBlock block) || !block.part().eye()) {
                return issue(Result.ANCHOR, anchor, anchor, null);
            }
        } else if (!(anchorBlock instanceof GatheringBlock array) || !array.isCore() || array.tier() != kind.gatheringTier) {
            return issue(Result.ANCHOR, anchor, anchor, null);
        }

        // Every orientation shares the anchor. Check possible claims even if a second rig has missing parts.
        for (var direction : Direction.Plane.HORIZONTAL) {
            var other = anchor.offset(layout(kind, direction).controller.offset());
            if (other.equals(controller)) continue;
            if (!level.hasChunkAt(other)) {
                if (!kind.mining()) return issue(Result.UNLOADED, anchor, other, null);
                continue;
            }
            if (matches(level.getBlockState(other), kind.controller, direction)) return issue(Result.SHARED, anchor, other, null);
        }

        var anchorChunk = new ChunkPos(anchor);
        for (var cell : layout.cells) {
            var pos = anchor.offset(cell.offset());
            // Natural eyes are generated far enough from chunk borders for a rig in every orientation.
            if (kind.mining() && !anchorChunk.equals(new ChunkPos(pos))) return issue(Result.BORDER, anchor, pos, null);
            if (!level.hasChunkAt(pos)) return issue(Result.UNLOADED, anchor, pos, null);
            if (!matches(level.getBlockState(pos), cell.part(), cell.facing())) return issue(Result.PART, anchor, pos, cell.part());
            if (cell.offset().getY() == 1 && !level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) {
                return issue(Result.SUPPORT, anchor, pos.below(), null);
            }
        }
        if (!kind.mining()) {
            var original = GatheringPattern.check(level, anchor);
            if (!original.formed()) return issue(Result.ORIGINAL_ARRAY, anchor, anchor.offset(original.offset()), null);
        }
        return issue(Result.OK, anchor, controller, null);
    }

    private static Check issue(Result result, BlockPos anchor, BlockPos issue, ProductionPart expected) {
        return new Check(result, anchor, issue, expected);
    }

    static boolean matches(BlockState state, ProductionPart part, Direction facing) {
        if (!(state.getBlock() instanceof ProductionBlock block) || block.part() != part) return false;
        return !part.directional() || state.getValue(ProductionBlock.FACING) == facing;
    }

    static ProductionBlockEntity resolveOutlet(Level level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof ProductionBlock block) || !block.part().outlet()) return null;
        Direction outletFacing = state.getValue(ProductionBlock.FACING);
        ProductionBlockEntity result = null;
        for (var kind : ProductionKind.values()) {
            var source = ProductionRules.get().layout(kind);
            if (source.outlet().part() != block.part()) continue;
            Direction facing = source.outlet().facing() == Direction.EAST ? outletFacing.getCounterClockWise() : outletFacing;
            var layout = layout(kind, facing);
            var controllerPos = pos.subtract(layout.outlet.offset()).offset(layout.controller.offset());
            if (!level.hasChunkAt(controllerPos) || !(level.getBlockEntity(controllerPos) instanceof ProductionBlockEntity root)
                    || root.kind() != kind || root.facing() != facing || !root.formation().formed()) continue;
            if (result != null) return null;
            result = root;
        }
        return result;
    }

    static BlockPos rotate(BlockPos pos, Direction fromNorth) {
        return switch (fromNorth) {
            case NORTH -> pos;
            case EAST -> new BlockPos(-pos.getZ(), pos.getY(), pos.getX());
            case SOUTH -> new BlockPos(-pos.getX(), pos.getY(), -pos.getZ());
            case WEST -> new BlockPos(pos.getZ(), pos.getY(), -pos.getX());
            default -> throw new IllegalArgumentException("Horizontal direction required");
        };
    }

    private static ProductionRules.Cell rotate(ProductionRules.Cell cell, Direction facing) {
        Direction dir = cell.facing() == Direction.EAST ? facing.getClockWise() : facing;
        return new ProductionRules.Cell(rotate(cell.offset(), facing), cell.part(), dir);
    }

    private static Map<ProductionKind, Map<Direction, Rotated>> build() {
        var layouts = new EnumMap<ProductionKind, Map<Direction, Rotated>>(ProductionKind.class);
        for (var kind : ProductionKind.values()) {
            var source = ProductionRules.get().layout(kind);
            var variants = new EnumMap<Direction, Rotated>(Direction.class);
            for (var direction : Direction.Plane.HORIZONTAL) {
                variants.put(direction, new Rotated(rotate(source.controller(), direction), rotate(source.outlet(), direction),
                        source.cells().stream().map(cell -> rotate(cell, direction)).toList()));
            }
            layouts.put(kind, Map.copyOf(variants));
        }
        return Map.copyOf(layouts);
    }

    private ProductionPattern() { }
}
