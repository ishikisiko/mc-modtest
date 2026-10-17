package com.example.myvillage.sim.combat;

/** Durable person state. An entity UUID and an in-flight attack never belong in a save. */
public record NpcCombatProfile(double health, double maximumHealth, long spirit, String weapon,
        long recoveryUntil, long revision, String lastAttacker, Body body) {
    public NpcCombatProfile {
        if (!Double.isFinite(health) || !Double.isFinite(maximumHealth) || maximumHealth <= 0
                || health < 0 || health > maximumHealth || spirit < 0 || recoveryUntil < 0 || revision < 0
                || weapon == null || weapon.length() > 32768 || lastAttacker == null || lastAttacker.length() > 256)
            throw new IllegalArgumentException("invalid_npc_combat_profile");
        if (lastAttacker.startsWith("npc:") || lastAttacker.startsWith("player:"))
            com.example.myvillage.sim.social.ActorRef.parse(lastAttacker);
    }

    public record Body(String dimension, String region, double x, double y, double z,
            double velocityX, double velocityY, double velocityZ, float fallDistance, int fireTicks) {
        public Body {
            if (dimension == null || dimension.length() > 256 || region == null || region.length() > 256
                    || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                    || Math.abs(x) > 30000000 || Math.abs(z) > 30000000 || Math.abs(y) > 30000000
                    || !Double.isFinite(velocityX) || !Double.isFinite(velocityY) || !Double.isFinite(velocityZ)
                    || !Float.isFinite(fallDistance) || fallDistance < 0 || fireTicks < 0)
                throw new IllegalArgumentException("invalid_npc_combat_body");
        }
    }
}
