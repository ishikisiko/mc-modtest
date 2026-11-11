package com.example.myvillage.estate;

/** Half-open private XZ column. The public perimeter is outside these bounds. */
public record EstateBounds(double minX, double minZ, double maxX, double maxZ) {
    public EstateBounds {
        if (!Double.isFinite(minX + minZ + maxX + maxZ) || minX >= maxX || minZ >= maxZ)
            throw new IllegalArgumentException("庄园范围无效");
    }
    public boolean overlaps(double x, double z, double radius) {
        return x + radius > minX && x - radius < maxX && z + radius > minZ && z - radius < maxZ;
    }
    public boolean crosses(double ax, double az, double bx, double bz, double radius) {
        if (!Double.isFinite(ax + az + bx + bz) || radius < 0) return false;
        double lo = 0, hi = 1;
        double[] a = {ax, az}, delta = {bx - ax, bz - az};
        double[] min = {minX - radius, minZ - radius}, max = {maxX + radius, maxZ + radius};
        for (int i = 0; i < 2; i++) {
            if (delta[i] == 0) { if (a[i] <= min[i] || a[i] >= max[i]) return false; }
            else {
                double p = (min[i] - a[i]) / delta[i], q = (max[i] - a[i]) / delta[i];
                lo = Math.max(lo, Math.min(p, q)); hi = Math.min(hi, Math.max(p, q));
                if (lo >= hi) return false;
            }
        }
        return lo < hi;
    }
}
