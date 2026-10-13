package com.example.myvillage.combat.runtime;

import com.example.myvillage.combat.session.CombatSession;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalDouble;

/** Continuous swept wave envelope. The earliest block touching ANY part stops the whole wave. */
public final class CombatWaveResolver {
    private CombatWaveResolver() {}

    /** Axis-aligned envelope of the pitched rectangular blade; conservative near oblique walls. */
    public static Vec3 extents(Vec3 direction, float yaw, double halfWidth, double halfHeight) {
        double radians = Math.toRadians(yaw);
        Vec3 right = new Vec3(Math.cos(radians), 0, Math.sin(radians));
        Vec3 up = direction.cross(right).normalize();
        return new Vec3(Math.abs(right.x) * halfWidth + Math.abs(up.x) * halfHeight,
                Math.abs(up.y) * halfHeight, Math.abs(right.z) * halfWidth + Math.abs(up.z) * halfHeight);
    }

    public static OptionalDouble contact(Vec3 start, Vec3 end, Vec3 extents, AABB box) {
        return CombatGeometry.segmentAabbContact(start, end, box.inflate(extents.x, extents.y, extents.z));
    }

    public static Resolution resolve(ServerPlayer player, CombatSession session, Vec3 start, Vec3 end,
                                     Vec3 extents, int maximumTargets) {
        AABB broad = new AABB(start, end).inflate(extents.x + 1.0E-6, extents.y + 1.0E-6, extents.z + 1.0E-6);
        double wall = Double.POSITIVE_INFINITY;
        if (!player.serverLevel().hasChunksAt(BlockPos.containing(broad.minX, broad.minY, broad.minZ),
                BlockPos.containing(broad.maxX, broad.maxY, broad.maxZ))) {
            wall = 0;
        } else {
            for (VoxelShape shape : player.level().getBlockCollisions(player, broad)) {
                for (AABB box : shape.toAabbs()) {
                    OptionalDouble hit = contact(start, end, extents, box);
                    if (hit.isPresent()) wall = Math.min(wall, hit.getAsDouble());
                }
            }
        }
        List<Contact> contacts = new ArrayList<>();
        for (Entity target : player.level().getEntities(player, broad,
                entity -> CombatHitResolver.legalTarget(player, entity, session))) {
            OptionalDouble hit = contact(start, end, extents, target.getBoundingBox());
            // Wall wins ties; a target touching the far side cannot be hit through it.
            if (hit.isPresent() && hit.getAsDouble() < wall) {
                contacts.add(new Contact(target, hit.getAsDouble(), start.lerp(end, hit.getAsDouble())));
            }
        }
        contacts.sort(Comparator.comparingDouble(Contact::fraction).thenComparingInt(c -> c.target().getId()));
        int remaining = session.remainingTargetCapacity(maximumTargets);
        if (contacts.size() > remaining) contacts = new ArrayList<>(contacts.subList(0, remaining));
        return new Resolution(List.copyOf(contacts), start.lerp(end, Math.min(1, wall)), wall <= 1);
    }
    public record Contact(Entity target, double fraction, Vec3 position) {}
    public record Resolution(List<Contact> contacts, Vec3 position, boolean blocked) {}
}
