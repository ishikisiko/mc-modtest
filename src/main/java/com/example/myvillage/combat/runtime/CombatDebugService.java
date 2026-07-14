package com.example.myvillage.combat.runtime;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class CombatDebugService {
    private static final int MAX_SAMPLE_PARTICLES = 20;
    private static final Set<UUID> ENABLED = new HashSet<>();

    private CombatDebugService() {
    }

    public static boolean setEnabled(ServerPlayer player, boolean enabled) {
        if (enabled && !player.createCommandSourceStack().hasPermission(2)) {
            return false;
        }
        return enabled ? ENABLED.add(player.getUUID()) : ENABLED.remove(player.getUUID());
    }

    public static boolean isEnabled(ServerPlayer player) {
        if (!player.createCommandSourceStack().hasPermission(2)) {
            ENABLED.remove(player.getUUID());
            return false;
        }
        return ENABLED.contains(player.getUUID());
    }

    public static void render(
            ServerPlayer player,
            CombatHitResolver.Resolution resolution,
            List<CombatHitResolver.TargetContact> successfulContacts) {
        if (!isEnabled(player)) {
            return;
        }
        int remaining = MAX_SAMPLE_PARTICLES;
        for (CombatGeometry.WorldSample sample : resolution.samples()) {
            if (remaining-- <= 0) {
                break;
            }
            Vec3 midpoint = sample.start().lerp(sample.end(), 0.5);
            player.serverLevel().sendParticles(
                    player, ParticleTypes.END_ROD, true,
                    midpoint.x, midpoint.y, midpoint.z,
                    1, 0.0, 0.0, 0.0, 0.0);
        }
        for (CombatHitResolver.TargetContact contact : successfulContacts) {
            Vec3 point = contact.contactPoint();
            player.serverLevel().sendParticles(
                    player, ParticleTypes.CRIT, true,
                    point.x, point.y, point.z,
                    3, 0.08, 0.08, 0.08, 0.0);
        }
    }

    public static void clear(UUID playerId) {
        ENABLED.remove(playerId);
    }

    public static void clearAll() {
        ENABLED.clear();
    }
}
