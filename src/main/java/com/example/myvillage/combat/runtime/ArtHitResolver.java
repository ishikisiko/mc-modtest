package com.example.myvillage.combat.runtime;

import com.example.myvillage.combat.art.ArtDefinition;
import com.example.myvillage.combat.art.ArtMath;
import com.example.myvillage.combat.session.CombatSession;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import java.util.*;

/** Bounded, wall-aware area queries. Reuses the base combat team's/PvP/invulnerability policy. */
public final class ArtHitResolver {
    private ArtHitResolver() {}
    public static List<CombatHitResolver.TargetContact> resolve(ServerPlayer player, CombatSession session,
            ArtDefinition.Phase phase, Vec3 feet) {
        Vec3 origin = feet.add(0,phase.height(),0);
        double reach = phase.reach();
        AABB broad = new AABB(origin.x-reach,origin.y-phase.halfHeight(),origin.z-reach,
                origin.x+reach,origin.y+phase.halfHeight(),origin.z+reach);
        List<CombatHitResolver.TargetContact> result = new ArrayList<>();
        int remaining = session.remainingTargetCapacity(phase.targets());
        if (remaining == 0) return List.of();
        Vec3 forward = new Vec3(ArtMath.forwardX(session.facingYaw()),0,ArtMath.forwardZ(session.facingYaw()));
        for (Entity target : player.serverLevel().getEntities(player,broad,
                entity -> CombatHitResolver.legalTarget(player,entity,session))) {
            AABB box = target.getBoundingBox();
            if (!ArtMath.inReach(origin.x,origin.z,box.minX,box.minZ,box.maxX,box.maxZ,reach)) continue;
            Vec3 contact = box.getCenter();
            if (phase.shape().equals("line")) {
                Optional<Vec3> intersection = box.inflate(phase.width()*.5,phase.halfHeight(),phase.width()*.5)
                        .clip(origin,origin.add(forward.scale(reach)));
                if (intersection.isEmpty() && !box.inflate(phase.width()*.5,phase.halfHeight(),phase.width()*.5).contains(origin)) continue;
                contact = intersection.orElse(origin);
            } else if (phase.shape().equals("fan") && !ArtMath.inArc(
                    contact.x-origin.x,contact.z-origin.z,session.facingYaw(),phase.arc())) continue;
            // Trace to an actual point inside the target, not an inflated envelope in front of a wall.
            Vec3 surface = new Vec3(Math.max(box.minX,Math.min(box.maxX,contact.x)),
                    Math.max(box.minY,Math.min(box.maxY,origin.y)),Math.max(box.minZ,Math.min(box.maxZ,contact.z)));
            BlockHitResult hit = player.level().clip(new ClipContext(origin,surface,
                    ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,player));
            if (hit.getType()==HitResult.Type.BLOCK && hit.getLocation().distanceToSqr(origin)+.0001 < surface.distanceToSqr(origin)) continue;
            result.add(new CombatHitResolver.TargetContact(target,surface.distanceToSqr(origin),surface));
        }
        result.sort(Comparator.comparingDouble(CombatHitResolver.TargetContact::distanceSquared)
                .thenComparingInt(c -> c.target().getId()));
        return CombatTargets.firstDistinct(result, contact -> CombatTargets.hitId(contact.target()), remaining);
    }
}
