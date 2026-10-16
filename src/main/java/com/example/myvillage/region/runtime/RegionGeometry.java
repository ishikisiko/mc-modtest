package com.example.myvillage.region.runtime;

/** Frozen, pure spatial transform for opt-in planned worlds. Legacy graphs have no transform. */
public record RegionGeometry(long seed, double radius, double warpScale, double warpAmplitude,
                             java.util.List<Reservation> reservations) {
    public record Reservation(String id, int minX, int minZ, int maxX, int maxZ) {}

    public RegionGeometry(long seed, double radius, double warpScale, double warpAmplitude) {
        this(seed, radius, warpScale, warpAmplitude, java.util.List.of());
    }

    public boolean reserved(int x, int z, int clearance) {
        for (Reservation r : reservations) if ((long)x >= (long)r.minX-clearance && (long)x <= (long)r.maxX+clearance
                && (long)z >= (long)r.minZ-clearance && (long)z <= (long)r.maxZ+clearance) return true;
        return false;
    }
    public RegionGeometry {
        reservations = java.util.List.copyOf(reservations);
        if (!Double.isFinite(radius) || radius <= 0 || !Double.isFinite(warpScale)
                || warpScale <= 0 || !Double.isFinite(warpAmplitude) || warpAmplitude < 0
                || warpAmplitude * 6 >= warpScale) {
            throw new IllegalArgumentException("Invalid or folding region geometry");
        }
    }

    public double[] warp(double x, double z) {
        return new double[] {x + noise(seed ^ 0x274B631CL, x / warpScale, z / warpScale) * warpAmplitude,
                z + noise(seed ^ 0x459EA391L, x / warpScale, z / warpScale) * warpAmplitude};
    }

    /** The derivative bound makes this fixed-point inverse contractive. Used only for markers. */
    public double[] unwarp(double x, double z) {
        double px = x, pz = z;
        for (int i = 0; i < 24; i++) {
            double[] q = warp(px, pz);
            px += x - q[0];
            pz += z - q[1];
        }
        return new double[] {px, pz};
    }

    public static long mix(long value) {
        value += 0x9E3779B97F4A7C15L;
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    public static long hash(long seed, long x, long z, long stream) {
        return mix(seed ^ mix(x) ^ Long.rotateLeft(mix(z), 29) ^ mix(stream));
    }

    public static double unit(long hash) { return (hash >>> 11) * 0x1.0p-53; }
    public static double smooth(double x) {
        double t = Math.max(0, Math.min(1, x));
        return t * t * (3 - 2 * t);
    }

    /** Continuous lattice value noise; floor is essential for negative coordinates. */
    public static double noise(long seed, double x, double z) {
        long ix = (long) StrictMath.floor(x), iz = (long) StrictMath.floor(z);
        double tx = smooth(x - ix), tz = smooth(z - iz);
        double a = unit(hash(seed, ix, iz, 0)), b = unit(hash(seed, ix + 1, iz, 0));
        double c = unit(hash(seed, ix, iz + 1, 0)), d = unit(hash(seed, ix + 1, iz + 1, 0));
        return 2 * ((a + (b - a) * tx) * (1 - tz) + (c + (d - c) * tx) * tz) - 1;
    }
}
