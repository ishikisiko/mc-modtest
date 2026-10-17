package com.example.myvillage.combat.art;

/** Pure shared arithmetic. No Minecraft classes, world access, clock or authority. */
public final class ArtMath {
    private ArtMath() {}
    public static boolean validSlot(int slot) { return slot == 0 || slot == 1; }
    public static long deadline(long now, int duration) {
        if (duration < 0) throw new IllegalArgumentException("Negative duration");
        return now > Long.MAX_VALUE - duration ? Long.MAX_VALUE : now + duration;
    }
    public static int remaining(long now, long deadline) {
        if (now >= deadline) return 0;
        long distance = deadline - now;
        return distance < 0 || distance > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) distance;
    }
    public static boolean guarding(int phase, int age, int activeStart, double reduction) {
        return reduction > 0 && phase == 0 && age >= 0 && age < activeStart;
    }
    /** Local forward is +Z, with Minecraft yaw: 0 south, 90 west. */
    public static double forwardX(double yaw) { return -Math.sin(Math.toRadians(yaw)); }
    public static double forwardZ(double yaw) { return Math.cos(Math.toRadians(yaw)); }
    public static boolean inArc(double dx, double dz, double yaw, double arc) {
        double distance = Math.hypot(dx, dz);
        return distance < 1e-9 || arc >= 360 ||
                (dx * forwardX(yaw) + dz * forwardZ(yaw)) / distance
                        >= Math.cos(Math.toRadians(arc * .5)) - 1e-9;
    }
    /** Circle versus an axis-aligned target footprint, not its centre alone. */
    public static boolean inReach(double x, double z, double minX, double minZ,
                                  double maxX, double maxZ, double reach) {
        double dx = x - Math.max(minX, Math.min(maxX, x));
        double dz = z - Math.max(minZ, Math.min(maxZ, z));
        return dx * dx + dz * dz <= reach * reach + 1e-9;
    }
}
