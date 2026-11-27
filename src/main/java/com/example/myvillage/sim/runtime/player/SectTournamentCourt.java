package com.example.myvillage.sim.runtime.player;

import com.example.myvillage.sect.SectBlueprint;
import com.example.myvillage.sim.SectView;
import com.example.myvillage.sim.runtime.avatar.GateRealizations;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Resolves the playing surface from the bound court, without building or loading any chunk. */
record SectTournamentCourt(ServerLevel level, int sect, BlockPos center, int half, Direction inward) {
    static SectTournamentCourt resolve(ServerLevel level, SectView sect) {
        var gate = GateRealizations.get(level).gate(sect.id()).orElse(null);
        if (gate == null || !sect.gateRealized() || !sect.state().equals("active")
                || gate.anchor().getX() != sect.gateX() || gate.anchor().getZ() != sect.gateZ()
                || !gate.matchesCurrentBlueprint()) return null;
        var blueprint = SectBlueprint.get(gate.variant());
        return new SectTournamentCourt(level, sect.id(), blueprint.world(gate.anchor(), blueprint.arena.center()),
                blueprint.arena.width() / 2, blueprint.arena.inward());
    }

    BlockPos first() { return center.relative(inward.getOpposite(), 3); }
    BlockPos second() { return center.relative(inward, 3); }
    BlockPos referee() { return entrance().relative(inward.getClockWise(), 3); }
    BlockPos entrance() { return center.relative(inward.getOpposite(), half - 1); }
    BlockPos exit() { return entrance(); }

    AABB ring() {
        return new AABB(center.getX() - half, center.getY() - .25, center.getZ() - half,
                center.getX() + half + 1, center.getY() + half + 1, center.getZ() + half + 1);
    }

    boolean onRing(Entity entity) {
        return entity.level() == level && ring().contains(entity.position());
    }

    boolean watches(Entity entity, int radius) {
        Vec3 feet = entity.position();
        return entity.level() == level && entity.isAlive() && Math.abs(feet.y - center.getY()) <= radius / 2.0
                && feet.distanceToSqr(Vec3.atBottomCenterOf(center)) <= (half + radius) * (double) (half + radius);
    }

    boolean ready() { return standable(first()) && standable(second()) && standable(referee()) && standable(exit()); }

    boolean standable(BlockPos at) {
        if (!level.isLoaded(at) || !level.isPositionEntityTicking(at) || !level.getWorldBorder().isWithinBounds(at)) return false;
        return level.getBlockState(at.below()).isFaceSturdy(level, at.below(), Direction.UP)
                && level.getBlockState(at).getCollisionShape(level, at).isEmpty()
                && level.getBlockState(at.above()).getCollisionShape(level, at.above()).isEmpty()
                && level.getFluidState(at).isEmpty() && level.getFluidState(at.above()).isEmpty();
    }

    Vec3 exitFor(Entity entity) {
        for (BlockPos at : new BlockPos[]{exit(), exit().relative(inward.getCounterClockWise(), 3), first(), second(), center}) {
            Vec3 feet = Vec3.atBottomCenterOf(at);
            if (standable(at) && level.noCollision(entity, entity.getBoundingBox().move(feet.subtract(entity.position())))) return feet;
        }
        return null;
    }
}
