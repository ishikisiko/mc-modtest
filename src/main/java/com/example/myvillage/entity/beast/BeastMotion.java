package com.example.myvillage.entity.beast;

/**
 * Vanilla mob motion on flat ordinary ground, as {@code LivingEntity.travel} runs it with no
 * movement input: each tick the entity first moves by its velocity, then the horizontal speed is
 * multiplied by 0.6 * 0.91 = 0.546 if it was on the ground at the start of that tick (0.91 if it
 * was airborne), and the vertical speed becomes {@code (vy - 0.08) * 0.98}. A lunge is applied on
 * the ground, so its first tick always takes ground friction, even when it lifts off.
 *
 * <p>Pure numbers, unit tested without a Minecraft bootstrap.
 */
public final class BeastMotion {
    static final double GROUND_RETAIN = 0.6 * 0.91;
    static final double AIR_RETAIN = 0.91;
    static final double GRAVITY = 0.08;
    static final double VERTICAL_DRAG = 0.98;
    private static final int SIMULATION_LIMIT_TICKS = 400;
    /** Simulation ends once the speed falls below this fraction of the start speed (and the beast has landed). */
    private static final double REST_FRACTION = 1.0E-9;

    private BeastMotion() {
    }

    /** Horizontal blocks travelled to rest per unit of initial horizontal speed, for vertical speed {@code up}. */
    public static double horizontalTravelPerUnitSpeed(double up) {
        return simulate(1.0, up).restDistance();
    }

    /** Simulates one lunge from flat ground to rest. */
    public static Flight simulate(double forwardSpeed, double up) {
        double travelled = 0.0;
        double speed = Math.max(0.0, forwardSpeed);
        double restSpeed = speed * REST_FRACTION;
        double height = 0.0;
        double vertical = Math.max(0.0, up);
        double peak = 0.0;
        double landing = 0.0;
        int airborneTicks = 0;
        boolean grounded = true;
        boolean landed = vertical <= 0.0;
        for (int tick = 0; tick < SIMULATION_LIMIT_TICKS && (speed > restSpeed || !landed); tick++) {
            travelled += speed;
            speed *= grounded ? GROUND_RETAIN : AIR_RETAIN;
            if (vertical > 0.0 || height > 0.0) {
                height += vertical;
                vertical = (vertical - GRAVITY) * VERTICAL_DRAG;
                peak = Math.max(peak, height);
                if (height <= 0.0) {
                    height = 0.0;
                    vertical = 0.0;
                }
            }
            grounded = height <= 0.0;
            if (!grounded) {
                airborneTicks++;
            } else if (!landed) {
                landed = true;
                landing = travelled;
            }
        }
        return new Flight(travelled, landed && up > 0.0 ? landing : 0.0, peak, airborneTicks);
    }

    /**
     * A simulated lunge: horizontal distance to rest, horizontal distance at touchdown (0 for a
     * ground lunge), peak height of the feet, and the number of ticks that end airborne.
     */
    public record Flight(double restDistance, double landingDistance, double peakHeight, int airborneTicks) {
    }
}
