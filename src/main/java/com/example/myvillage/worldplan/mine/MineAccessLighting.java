package com.example.myvillage.worldplan.mine;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;

/** 只照明有连续岩壁的人工连接段；朝向随矿口一同旋转。 */
public final class MineAccessLighting {
    public record Torch(int x, int y, int z, Direction facing) {
        public BlockPos position(StarterMineLayout layout) {
            return new BlockPos(layout.worldX(x, z), layout.y + y, layout.worldZ(x, z));
        }

        public BlockState state(StarterMineLayout layout) {
            Direction worldFacing = Direction.from2DDataValue(facing.get2DDataValue() + layout.rotation);
            return Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, worldFacing);
        }
    }

    private MineAccessLighting() {}

    public static List<Torch> plan(StarterMineDefinition def, List<StarterMineCavePlan.Access> access, int wallEndZ) {
        var rules = def.caves;
        int firstZ = rules.flatEndZ + 2;
        var torches = new ArrayList<Torch>();
        for (var column : access) {
            if (column.x() != def.entry.x() || column.z() < firstZ || column.z() > wallEndZ
                    || (column.z() - firstZ) % rules.torchSpacing != 0) continue;
            int y = (int) Math.ceil(column.feet2() / 2.0) + rules.torchHeight;
            torches.add(new Torch(def.entry.x() - rules.connectionRadius, y, column.z(), Direction.EAST));
            torches.add(new Torch(def.entry.x() + rules.connectionRadius, y, column.z(), Direction.WEST));
        }
        return List.copyOf(torches);
    }
}
