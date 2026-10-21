package com.example.myvillage.gathering;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

/** Every read is bounded and refuses unloaded chunks. The UI uses these same node offsets. */
public final class GatheringPattern {
    public record Check(int issue, BlockPos offset) {
        public boolean formed() { return issue == 0; }
    }
    public static final Check OK = new Check(0, BlockPos.ZERO);
    public interface Access {
        boolean loaded(BlockPos pos);
        boolean core(BlockPos pos);
        boolean node(BlockPos pos);
        boolean supported(BlockPos pos);
        boolean clear(BlockPos pos);
    }
    public static List<BlockPos> nodes() {
        int r = GatheringRules.get().radius();
        return List.of(new BlockPos(0, 1, -r), new BlockPos(r, 1, 0),
                new BlockPos(0, 1, r), new BlockPos(-r, 1, 0));
    }
    public static Check check(Level level, BlockPos core) {
        return check(new Access() {
            public boolean loaded(BlockPos pos) { return level.hasChunkAt(pos); }
            public boolean core(BlockPos pos) { return level.getBlockState(pos).is(GatheringContent.CORE.get()); }
            public boolean node(BlockPos pos) { return level.getBlockState(pos).is(GatheringContent.NODE.get()); }
            public boolean supported(BlockPos pos) { return level.getBlockState(pos).isFaceSturdy(level, pos, Direction.UP); }
            public boolean clear(BlockPos pos) { return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty() && level.getFluidState(pos).isEmpty(); }
        }, core);
    }
    public static Check check(Access world, BlockPos core) {
        if (!world.loaded(core)) return new Check(7, BlockPos.ZERO);
        if (!world.core(core)) return new Check(9, BlockPos.ZERO);
        var nodes = nodes();
        for (int i = 0; i < nodes.size(); i++) {
            BlockPos offset = nodes.get(i), pos = core.offset(offset);
            if (!world.loaded(pos)) return new Check(7, offset);
            if (!world.node(pos)) return new Check(i + 1, offset);
            // A node cannot be claimed by any other placed core, including an incomplete array.
            for (BlockPos candidate : nodes) {
                BlockPos other = pos.subtract(candidate);
                if (other.equals(core)) continue;
                if (!world.loaded(other)) return new Check(7, other.subtract(core));
                if (world.core(other)) return new Check(8, offset);
            }
        }
        int radius = GatheringRules.get().radius(), inner = GatheringRules.get().innerRadius();
        for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) {
            BlockPos offset = new BlockPos(x, 0, z), pos = core.offset(offset);
            if (!world.loaded(pos)) return new Check(7, offset);
            if (!world.supported(pos)) return new Check(5, offset);
            if (Math.abs(x) <= inner && Math.abs(z) <= inner) for (int y = 1; y <= 2; y++) {
                BlockPos above = pos.above(y);
                if (!world.clear(above)) return new Check(6, new BlockPos(x, y, z));
            }
        }
        return OK;
    }
    private GatheringPattern() { }
}
