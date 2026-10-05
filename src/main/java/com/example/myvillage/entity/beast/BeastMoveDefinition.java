package com.example.myvillage.entity.beast;

import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/**
 * One beast attack move (schema 1, see {@code data/myvillage/beast/*.json}). Ticks are server
 * ticks counted from 0 at move start, in the beast's own tick (a hit-stop freeze pauses them).
 *
 * <p>The record checks its own cross-field invariants; the loader reports the same rules with the
 * file and field named before it gets here.
 */
public record BeastMoveDefinition(
        ResourceLocation id,
        String animation,
        int totalTicks,
        int windupTicks,
        int turnLockTick,
        BeastTickRange activeTicks,
        BeastTickRange immuneTicks,
        double damageMultiplier,
        int maximumTargets,
        UseRange useRange,
        int cooldownTicks,
        int weight,
        Lunge lunge,
        HitBox hit,
        Knockback knockback) {

    public BeastMoveDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(animation, "animation");
        Objects.requireNonNull(activeTicks, "activeTicks");
        Objects.requireNonNull(immuneTicks, "immuneTicks");
        Objects.requireNonNull(useRange, "useRange");
        Objects.requireNonNull(lunge, "lunge");
        Objects.requireNonNull(hit, "hit");
        Objects.requireNonNull(knockback, "knockback");
        if (totalTicks <= 0) {
            throw new IllegalArgumentException("total_ticks must be positive");
        }
        if (windupTicks < 0 || windupTicks > activeTicks.first()) {
            throw new IllegalArgumentException("windup_ticks must satisfy 0 <= windup_ticks <= active start");
        }
        if (activeTicks.last() >= totalTicks || immuneTicks.last() >= totalTicks) {
            throw new IllegalArgumentException("active and immune ticks must end before total_ticks");
        }
        if (turnLockTick < 0 || turnLockTick > windupTicks) {
            throw new IllegalArgumentException("turn_lock_tick must satisfy 0 <= turn_lock_tick <= windup_ticks");
        }
        if (lunge.tick() < turnLockTick || lunge.tick() > activeTicks.last()) {
            throw new IllegalArgumentException("lunge.tick must satisfy turn_lock_tick <= tick <= active end");
        }
        if (!(damageMultiplier > 0.0) || maximumTargets <= 0 || cooldownTicks < 0 || weight <= 0) {
            throw new IllegalArgumentException("damage_multiplier, maximum_targets and weight must be positive"
                    + " and cooldown_ticks not negative");
        }
    }

    public BeastMovePhase phase(int tick) {
        if (tick >= totalTicks) {
            return BeastMovePhase.FINISHED;
        }
        if (tick < windupTicks) {
            return BeastMovePhase.WINDUP;
        }
        if (tick < activeTicks.first()) {
            return BeastMovePhase.RELEASE;
        }
        if (tick <= activeTicks.last()) {
            return BeastMovePhase.ACTIVE;
        }
        return BeastMovePhase.RECOVERY;
    }

    /** The beast still turns toward its target on this tick; the yaw and aim lock at turn_lock_tick. */
    public boolean turnsAt(int tick) {
        return tick >= 0 && tick < turnLockTick;
    }

    public boolean locksAt(int tick) {
        return tick == turnLockTick;
    }

    public boolean activeAt(int tick) {
        return activeTicks.contains(tick);
    }

    /** The beast resists stagger and knockback on this tick. */
    public boolean immuneAt(int tick) {
        return immuneTicks.contains(tick);
    }

    public boolean lungesAt(int tick) {
        return tick == lunge.tick();
    }

    /** Horizontal centre-to-centre distance window in which the move may start. */
    public record UseRange(double minimum, double maximum) {
        public UseRange {
            if (!Double.isFinite(minimum) || !Double.isFinite(maximum) || minimum < 0.0 || maximum < minimum) {
                throw new IllegalArgumentException("use_range must satisfy 0 <= min <= max");
            }
        }

        public boolean contains(double distance) {
            return distance >= minimum && distance <= maximum;
        }

        public double middle() {
            return (minimum + maximum) * 0.5;
        }
    }

    /**
     * One impulse along the locked yaw at {@code tick}: forward speed (blocks per tick) chosen to
     * land near the locked aim point, clamped to {@code [forwardMin, forwardMax]}; {@code up} is
     * the vertical speed (0 keeps the beast on the ground).
     */
    public record Lunge(int tick, double forwardMin, double forwardMax, double up) {
        public Lunge {
            if (tick < 0) {
                throw new IllegalArgumentException("lunge.tick must not be negative");
            }
            if (!Double.isFinite(forwardMin) || !Double.isFinite(forwardMax) || forwardMin < 0.0
                    || forwardMax < forwardMin || forwardMax > 4.0) {
                throw new IllegalArgumentException("lunge forward speeds must satisfy 0 <= min <= max <= 4");
            }
            if (!Double.isFinite(up) || up < 0.0 || up > 1.5) {
                throw new IllegalArgumentException("lunge.up must be in 0..1.5 blocks per tick");
            }
        }

        /**
         * Forward speed that carries the beast {@code desiredTravel} blocks to rest under vanilla
         * drag for this lunge's {@code up}, clamped to the data range.
         */
        public double forwardSpeed(double desiredTravel) {
            double speed = Math.max(0.0, desiredTravel) / BeastMotion.horizontalTravelPerUnitSpeed(up);
            return Math.max(forwardMin, Math.min(forwardMax, speed));
        }
    }

    /**
     * Hit volume in the beast's locked local frame, measured from its feet position:
     * {@code forward} along the locked yaw, {@code halfWidth} to each side, {@code low..high} in
     * height.
     */
    public record HitBox(double near, double far, double halfWidth, double low, double high) {
        public HitBox {
            if (!Double.isFinite(near) || !Double.isFinite(far) || far <= near) {
                throw new IllegalArgumentException("hit.forward must be [near, far] with near < far");
            }
            if (!Double.isFinite(halfWidth) || !(halfWidth > 0.0)) {
                throw new IllegalArgumentException("hit.half_width must be positive");
            }
            if (!Double.isFinite(low) || !Double.isFinite(high) || high <= low) {
                throw new IllegalArgumentException("hit.height must be [low, high] with low < high");
            }
        }

        /** Distance ahead of the beast's feet at which the box is centred along the yaw. */
        public double standoff() {
            return (near + far) * 0.5;
        }
    }

    /** Push given to each victim: horizontal speed along the locked yaw and upward speed. */
    public record Knockback(double strength, double lift) {
        public Knockback {
            if (!Double.isFinite(strength) || strength < 0.0 || strength > 3.0) {
                throw new IllegalArgumentException("knockback.strength must be in 0..3");
            }
            if (!Double.isFinite(lift) || lift < 0.0 || lift > 1.0) {
                throw new IllegalArgumentException("knockback.lift must be in 0..1");
            }
        }
    }
}
