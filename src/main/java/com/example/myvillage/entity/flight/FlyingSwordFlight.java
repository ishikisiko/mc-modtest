package com.example.myvillage.entity.flight;

import com.example.myvillage.network.FlyingSwordInputFlags;

/** Pure authority policy, shared by the entity and focused source tests. No player transforms accepted. */
public final class FlyingSwordFlight {
    public enum Phase { TAKEOFF, HOVER, CRUISE, BOOST, LANDING, EMPTY_DESCENT }
    public record Motion(double x, double y, double z) { }
    private FlyingSwordFlight() { }
    public static boolean fresh(long now, long last, int timeout) {
        return last != Long.MIN_VALUE && now >= last && now - last <= timeout;
    }
    public static Phase phase(boolean landing, long qi, boolean creative, byte flags, int age, FlyingSwordRules r) {
        if (!creative && qi <= 0) return Phase.EMPTY_DESCENT;
        if (landing) return Phase.LANDING;
        if (age < r.takeoff_ticks()) return Phase.TAKEOFF;
        boolean horizontal = axis(flags, 1, 2) != 0 || axis(flags, 4, 8) != 0;
        if (horizontal && FlyingSwordInputFlags.has(flags, FlyingSwordInputFlags.BOOST)
                && (creative || qi > r.low_qi())) return Phase.BOOST;
        return horizontal ? Phase.CRUISE : Phase.HOVER;
    }
    public static long cost(Phase phase, FlyingSwordRules r) {
        return phase == Phase.EMPTY_DESCENT ? 0 : phase == Phase.BOOST ? r.boost_cost() : r.cruise_cost();
    }
    public static Motion step(Motion v, byte flags, float yaw, Phase phase, int emptyTicks, FlyingSwordRules r) {
        double f = axis(flags, 1, 2), s = axis(flags, 4, 8), up = axis(flags, 16, 32);
        double norm = Math.max(1, Math.hypot(f, s)); f /= norm; s /= norm;
        double a = Math.toRadians(yaw), dx = -Math.sin(a)*f + Math.cos(a)*s, dz = Math.cos(a)*f + Math.sin(a)*s;
        double speed = phase == Phase.BOOST ? r.boost_speed() : r.cruise_speed();
        boolean empty = phase == Phase.EMPTY_DESCENT;
        if (empty) { speed = emptyTicks <= r.empty_steering_ticks() ? r.empty_horizontal_speed() : 0; }
        double x = f == 0 && s == 0 ? v.x*r.drag() : v.x+(dx*speed-v.x)*r.response();
        double z = f == 0 && s == 0 ? v.z*r.drag() : v.z+(dz*speed-v.z)*r.response();
        double h = Math.hypot(x,z); if (h > speed) { x *= speed/h; z *= speed/h; }
        double targetY = empty || phase == Phase.LANDING ? -r.landing_speed()
                : phase == Phase.TAKEOFF ? r.vertical_speed() : up*r.vertical_speed();
        double y = v.y+(targetY-v.y)*r.response();
        // Empty flight can never climb or hover, even immediately after a rising boost.
        if (empty || phase == Phase.LANDING) y = Math.min(-r.landing_speed(), y);
        return new Motion(x,y,z);
    }
    private static int axis(byte f, int pos, int neg) { return ((f & pos)!=0?1:0)-((f & neg)!=0?1:0); }
}
