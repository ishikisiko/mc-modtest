package com.example.myvillage.sim.runtime.life;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.AABB;

/** Ground navigation for resident bodies, including the full collision sweep between floors. */
public final class NpcLifeNavigation extends GroundPathNavigation {
    public NpcLifeNavigation(Mob mob, Level level) { super(mob, level); }

    @Override protected PathFinder createPathFinder(int budget) {
        nodeEvaluator = new CollisionWalkNodeEvaluator();
        nodeEvaluator.setCanPassDoors(true);
        return new PathFinder(nodeEvaluator, budget);
    }

    // Vanilla can skip a stair/corner node solely from direction, without checking the swept body.
    @Override public boolean canCutCorner(PathType type) { return false; }

    @Override public void tick() {
        if (!isDone() && !mob.onGround() && !mob.isInLiquid() && !mob.isPassenger()
                && mob.getY() > path.getNextNode().y) {
            // Vanilla skips a descending node as soon as X/Z enter its block, even while the body
            // is still above the landing. Hold that waypoint until the actual descent is finished.
            var next = path.getNextEntityPos(mob);
            mob.getMoveControl().setWantedPosition(next.x, getGroundY(next), next.z, speedModifier);
            return;
        }
        super.tick();
    }

    private static final class CollisionWalkNodeEvaluator extends WalkNodeEvaluator {
        @Override public int getNeighbors(Node[] neighbors, Node from) {
            int count = super.getNeighbors(neighbors, from), accepted = 0;
            for (int i = 0; i < count; i++) {
                Node to = neighbors[i];
                if (clearStep(currentContext.level(), from, to, mob.getBbWidth(), mob.getBbHeight(), canOpenDoors()))
                    neighbors[accepted++] = to;
            }
            return accepted;
        }
    }

    /**
     * Vanilla classifies most non-full blocks by path type, but does not sweep a normal WALKABLE
     * descent through the wall/ceiling above its landing. Check both endpoints, lift/drop and the
     * horizontal body corridor. Only hand-openable doors may be ignored; OpenDoorGoal handles them.
     * The getter is the already bounded navigation region, never a world chunk request.
     */
    static boolean clearStep(BlockGetter blocks, Node from, Node to, double width, double height, boolean doors) {
        double fy = WalkNodeEvaluator.getFloorLevel(blocks, new BlockPos(from.x, from.y, from.z));
        double ty = WalkNodeEvaluator.getFloorLevel(blocks, new BlockPos(to.x, to.y, to.z));
        double high = Math.max(fy, ty);
        AABB start = body(from.x, fy, from.z, width, height);
        AABB end = body(to.x, ty, to.z, width, height);
        AABB horizontal = body(from.x, high, from.z, width, height)
                .minmax(body(to.x, high, to.z, width, height));
        return clear(blocks, start.expandTowards(0, high - fy, 0), doors)
                && clear(blocks, end.expandTowards(0, high - ty, 0), doors)
                && clear(blocks, horizontal, doors);
    }

    private static AABB body(int x, double y, int z, double width, double height) {
        double half = width / 2;
        return new AABB(x + .5 - half, y + 1.0e-5, z + .5 - half,
                x + .5 + half, y + height - 1.0e-5, z + .5 + half);
    }

    private static boolean clear(BlockGetter blocks, AABB body, boolean doors) {
        // A neighboring fence/wall can extend beyond the block above which the body stands.
        for (BlockPos at : BlockPos.betweenClosed(BlockPos.containing(body.minX, body.minY - 1, body.minZ),
                BlockPos.containing(body.maxX, body.maxY, body.maxZ))) {
            var state = blocks.getBlockState(at);
            if (doors && DoorBlock.isWoodenDoor(state)) continue;
            for (AABB shape : state.getCollisionShape(blocks, at).toAabbs()) {
                if (body.intersects(shape.move(at))) return false;
            }
        }
        return true;
    }
}
