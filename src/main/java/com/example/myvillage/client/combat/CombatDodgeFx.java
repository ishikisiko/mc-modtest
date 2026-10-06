package com.example.myvillage.client.combat;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Presentation of a 身法 dodge the server started: the local player's FOV surge and lean, and
 * the cloud afterimages every dodging player leaves at its heels while the dodge lasts. Nothing
 * here moves anyone (the server's impulse arrives as the vanilla motion packet) or decides
 * anything; timing reads only {@link ClientCombatClock}. State is per entity id and dropped when
 * the dodge ends, the entity leaves the level, or the client leaves the world.
 */
final class CombatDodgeFx {
    /** FOV widening (degrees) as the local player's dodge launches. */
    static final float LOCAL_FOV_SURGE = 6.0F;
    /** Roll lean (degrees) for a purely sideways dodge; straight dodges do not lean. */
    static final float LOCAL_LEAN_DEGREES = 1.2F;
    /** Local ticks between two dodge intents sent by this client. */
    static final int INTENT_INTERVAL_TICKS = 4;
    /** How far behind the feet (blocks, against the travel direction) the afterimages appear. */
    static final double AFTERIMAGE_BACK_OFFSET = 0.45;
    private static final double AFTERIMAGE_DRIFT = 0.03;
    private static final double AFTERIMAGE_SPREAD = 0.18;

    private static final Map<Integer, Dodge> DODGES = new HashMap<>();

    private CombatDodgeFx() {
    }

    /**
     * A dodge of {@code entityId} that started at local tick {@code localStartTick} (the server's
     * start mapped once on arrival) and lasts {@code durationTicks}; replaces an earlier one.
     */
    static void start(int entityId, long localStartTick, int durationTicks, float directionYaw) {
        if (durationTicks <= 0) {
            return;
        }
        DODGES.put(entityId, new Dodge(localStartTick, durationTicks, directionYaw));
    }

    /** One client tick of afterimages; drops finished dodges and entities no longer in the level. */
    static void clientTick(ClientLevel level, long now) {
        if (DODGES.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<Integer, Dodge>> iterator = DODGES.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, Dodge> entry = iterator.next();
            Dodge dodge = entry.getValue();
            Entity entity = level.getEntity(entry.getKey());
            if (!(entity instanceof AbstractClientPlayer player)
                    || player.isRemoved()
                    || !player.isAlive()
                    || remainingTicks(dodge.startTick(), dodge.durationTicks(), now) <= 0) {
                iterator.remove();
                continue;
            }
            boolean launch = !dodge.launched;
            dodge.launched = true;
            int age = (int) Math.min(Integer.MAX_VALUE, Math.max(0L, now - dodge.startTick()));
            int count = afterimageCount(age, dodge.durationTicks(), launch);
            if (count > 0) {
                emit(level, player, dodge, count, launch);
            }
        }
    }

    private static void emit(ClientLevel level, AbstractClientPlayer player, Dodge dodge, int count, boolean first) {
        RandomSource random = level.getRandom();
        double[] behind = behindOffset(dodge.directionYaw(), AFTERIMAGE_BACK_OFFSET);
        double[] drift = behindOffset(dodge.directionYaw(), AFTERIMAGE_DRIFT);
        for (int i = 0; i < count; i++) {
            double x = player.getX() + behind[0] + (random.nextDouble() - 0.5) * AFTERIMAGE_SPREAD;
            double y = player.getY() + 0.1 + random.nextDouble() * 0.5;
            double z = player.getZ() + behind[1] + (random.nextDouble() - 0.5) * AFTERIMAGE_SPREAD;
            level.addParticle(ParticleTypes.CLOUD, x, y, z, drift[0], 0.01, drift[1]);
        }
        if (first) {
            // The launch kicks a puff of dust off the ground where the body left.
            level.addParticle(ParticleTypes.POOF,
                    player.getX() + behind[0], player.getY() + 0.05, player.getZ() + behind[1],
                    drift[0], 0.0, drift[1]);
        }
    }

    /** Forgets one entity (respawn, removal). */
    static void forget(int entityId) {
        DODGES.remove(entityId);
    }

    /** Leaving the world or changing level: entity ids no longer mean the same entities. */
    static void clear() {
        DODGES.clear();
    }

    static boolean active(int entityId) {
        return DODGES.containsKey(entityId);
    }

    /** Whole ticks of the dodge still to play at {@code now}; never negative. */
    static int remainingTicks(long startTick, int durationTicks, long now) {
        long remaining = startTick + durationTicks - now;
        return remaining <= 0L ? 0 : (int) Math.min(Integer.MAX_VALUE, remaining);
    }

    /**
     * Cloud afterimages for the dodge's tick {@code age}: a burst on the first tick this client
     * draws it ({@code launch}, which is never age 0: the start arrives before the tick that
     * draws it), a trail through the first half, a thin wisp after, none outside the dodge.
     */
    static int afterimageCount(int age, int durationTicks, boolean launch) {
        if (age < 0 || age >= durationTicks) {
            return 0;
        }
        if (launch) {
            return 4;
        }
        return age < (durationTicks + 1) / 2 ? 2 : 1;
    }

    /**
     * Horizontal offset {x, z} of {@code distance} blocks against the travel direction
     * {@code directionYaw} (Minecraft yaw: travel is (-sin, cos)).
     */
    static double[] behindOffset(float directionYaw, double distance) {
        double radians = Math.toRadians(directionYaw);
        return new double[] {Math.sin(radians) * distance, -Math.cos(radians) * distance};
    }

    /**
     * Roll lean for a dodge travelling along {@code directionYaw} seen from {@code viewYaw}: the
     * sideways share of the motion (left positive, as {@code DodgeDirection#left}) times
     * {@link #LOCAL_LEAN_DEGREES}; forward and back dodges do not lean.
     */
    static float leanFor(float directionYaw, float viewYaw) {
        float relative = Mth.wrapDegrees(directionYaw - viewYaw);
        // Left of facing is yaw - 90, so a left dodge has relative -90 and sin = -1.
        float sideways = (float) -Math.sin(Math.toRadians(relative));
        return Math.abs(sideways) < 1.0E-4F ? 0.0F : sideways * LOCAL_LEAN_DEGREES;
    }

    /** True when a dodge intent at {@code now} is at least {@link #INTENT_INTERVAL_TICKS} after the last. */
    static boolean intentDue(long lastIntentTick, long now) {
        return lastIntentTick == Long.MIN_VALUE || now - lastIntentTick >= INTENT_INTERVAL_TICKS;
    }

    /** Test hook. */
    static Dodge dodgeFor(int entityId) {
        return DODGES.get(entityId);
    }

    static final class Dodge {
        private final long startTick;
        private final int durationTicks;
        private final float directionYaw;
        /** The launch burst has been drawn. */
        private boolean launched;

        Dodge(long startTick, int durationTicks, float directionYaw) {
            this.startTick = startTick;
            this.durationTicks = durationTicks;
            this.directionYaw = directionYaw;
        }

        long startTick() {
            return startTick;
        }

        int durationTicks() {
            return durationTicks;
        }

        float directionYaw() {
            return directionYaw;
        }
    }
}
