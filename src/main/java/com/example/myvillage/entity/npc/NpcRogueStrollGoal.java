package com.example.myvillage.entity.npc;

import com.example.myvillage.npc.NpcInteractionRules;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

/** Wander around the saved birthplace, with ordinary navigation back after displacement. */
final class NpcRogueStrollGoal extends WaterAvoidingRandomStrollGoal {
    private final NpcEntity npc;
    private boolean returning;
    NpcRogueStrollGoal(NpcEntity npc, double speed) { super(npc, speed); this.npc = npc; }
    @Override public boolean canUse() {
        if (npc.conversationPaused() || npc.getTarget() != null) return false;
        if (npc.isTemporaryRogue() && npc.rogueAnchor() != null && !inside(npc.position())) {
            Vec3 pos = getPosition();
            if (pos == null) return false;
            wantedX = pos.x; wantedY = pos.y; wantedZ = pos.z;
            return true;
        }
        return super.canUse();
    }
    @Override protected Vec3 getPosition() {
        if (!npc.isTemporaryRogue() || npc.rogueAnchor() == null) return super.getPosition();
        if (!inside(npc.position())) {
            var anchor = Vec3.atBottomCenterOf(npc.rogueAnchor());
            if (npc.level().hasChunkAt(npc.rogueAnchor())) return anchor;
            return null;
        }
        Vec3 candidate = super.getPosition();
        return candidate != null && inside(candidate) ? candidate : null;
    }
    @Override public void start() {
        if (!npc.isTemporaryRogue()) { super.start(); return; }
        returning = !inside(npc.position());
        npc.getNavigation().stop();
        var target = new net.minecraft.core.BlockPos((int)Math.floor(wantedX), (int)Math.floor(wantedY), (int)Math.floor(wantedZ));
        if (!npc.level().hasChunkAt(target)) return;
        Path path = npc.getNavigation().createPath(wantedX, wantedY, wantedZ, 0);
        if (path == null) return;
        double allowed = Math.max(NpcInteractionRules.get().wanderRadius(), distance(npc.position()));
        // A destination inside the range must not hide a detour outside it.
        for (int i = 0; i < path.getNodeCount(); i++) {
            var node = path.getNode(i);
            Vec3 pos = new Vec3(node.x + .5, node.y, node.z + .5);
            if (!npc.level().hasChunkAt(node.asBlockPos()) || distance(pos) > allowed) return;
        }
        npc.getNavigation().moveTo(path, speedModifier);
    }
    @Override public boolean canContinueToUse() {
        return !npc.conversationPaused() && npc.getTarget() == null
                && (!npc.isTemporaryRogue() || returning || inside(npc.position())) && super.canContinueToUse();
    }
    private double distance(Vec3 pos) {
        return pos.distanceTo(Vec3.atBottomCenterOf(npc.rogueAnchor()));
    }
    private boolean inside(Vec3 pos) { return distance(pos) <= NpcInteractionRules.get().wanderRadius(); }
}
