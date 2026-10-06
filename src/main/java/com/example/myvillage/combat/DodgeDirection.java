package com.example.myvillage.combat;

/**
 * The player's movement input at the moment of a dodge, quantised to eight directions relative to
 * the view yaw, or {@link #NONE} (no movement key held; the server treats it as a back step).
 * This is input, not authority: the server decides whether the dodge happens, how far it goes and
 * how long it protects. Ordinals go over the wire, so append new values last.
 */
public enum DodgeDirection {
    NONE(0, 0),
    FORWARD(1, 0),
    FORWARD_LEFT(1, 1),
    LEFT(0, 1),
    BACK_LEFT(-1, 1),
    BACK(-1, 0),
    BACK_RIGHT(-1, -1),
    RIGHT(0, -1),
    FORWARD_RIGHT(1, -1);

    private final int forward;
    private final int left;

    DodgeDirection(int forward, int left) {
        this.forward = forward;
        this.left = left;
    }

    /** +1 toward the view direction, -1 away from it, 0 neither. */
    public int forward() {
        return forward;
    }

    /** +1 to the player's left, -1 to the right, 0 neither (vanilla's {@code leftImpulse} sign). */
    public int left() {
        return left;
    }

    /**
     * Dead zone of the quantisation, strictly below vanilla's sneaking input scale (0.3), so a
     * sneaking player's held key still reads as a direction instead of a back step.
     */
    public static final float DEAD_ZONE = 0.2F;

    /** Quantises vanilla's {@code forwardImpulse}/{@code leftImpulse} (dead zone {@link #DEAD_ZONE}). */
    public static DodgeDirection fromInput(float forwardImpulse, float leftImpulse) {
        int f = forwardImpulse > DEAD_ZONE ? 1 : forwardImpulse < -DEAD_ZONE ? -1 : 0;
        int l = leftImpulse > DEAD_ZONE ? 1 : leftImpulse < -DEAD_ZONE ? -1 : 0;
        for (DodgeDirection direction : values()) {
            if (direction.forward == f && direction.left == l) {
                return direction;
            }
        }
        return NONE;
    }

    /**
     * World yaw (degrees, Minecraft convention: 0 = +Z, 90 = -X) of this direction for a player
     * looking along {@code viewYaw}. {@link #NONE} is a back step.
     */
    public float worldYaw(float viewYaw) {
        int f = this == NONE ? -1 : forward;
        int l = this == NONE ? 0 : left;
        // Minecraft: forward = (-sin yaw, cos yaw); left = (cos yaw, sin yaw)... left of facing is yaw - 90.
        double offset = Math.toDegrees(Math.atan2(l, f));
        return (float) (viewYaw - offset);
    }
}
