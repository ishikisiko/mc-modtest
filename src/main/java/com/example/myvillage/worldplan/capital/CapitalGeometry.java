package com.example.myvillage.worldplan.capital;

import java.util.Objects;

/** Block units, +X east, +Y up, +Z south. Bounds are closed geometric envelopes. */
public final class CapitalGeometry {
    private CapitalGeometry() {}

    public record Point(double x, double y, double z) {
        public Point {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z))
                throw new IllegalArgumentException("城市坐标必须有限");
        }
    }

    public record Bounds(Point min, Point max) {
        public Bounds {
            Objects.requireNonNull(min); Objects.requireNonNull(max);
            if (min.x > max.x || min.y > max.y || min.z > max.z)
                throw new IllegalArgumentException("城市包络的 min 必须不大于 max");
        }

        public boolean contains(Point point) {
            return point.x >= min.x && point.x <= max.x && point.y >= min.y && point.y <= max.y
                    && point.z >= min.z && point.z <= max.z;
        }

        public boolean contains(Bounds other) { return contains(other.min) && contains(other.max); }

        /** Reservations also protect supporting terrain: vertical separation does not permit overlap. */
        public boolean intersectsHorizontally(Bounds other) {
            return min.x <= other.max.x && max.x >= other.min.x
                    && min.z <= other.max.z && max.z >= other.min.z;
        }

        public Bounds paddedHorizontally(int padding) {
            if (padding < 0) throw new IllegalArgumentException("城市预留边距不能为负");
            return new Bounds(new Point(min.x - padding, min.y, min.z - padding),
                    new Point(max.x + padding, max.y, max.z + padding));
        }
    }

    /** Rotation is around the authored origin, including layouts with negative local coordinates. */
    public record Placement(int x, int y, int z, int rotationQuarters) {
        public Placement {
            if (rotationQuarters < 0 || rotationQuarters > 3)
                throw new IllegalArgumentException("城市旋转必须为 0..3 个四分之一圈");
        }

        /** Positive rotation takes east (+X) to south (+Z). */
        public Point toWorld(Point local) {
            double rx = local.x, rz = local.z;
            for (int turn = 0; turn < rotationQuarters; turn++) {
                double nextX = -rz; rz = rx; rx = nextX;
            }
            return new Point(x + rx, y + local.y, z + rz);
        }

        public String toWorldFacing(String local) {
            int index = switch (local) {
                case "north" -> 0; case "east" -> 1; case "south" -> 2; case "west" -> 3;
                default -> throw new IllegalArgumentException("未知城市入口朝向: " + local);
            };
            return switch ((index + rotationQuarters) % 4) {
                case 0 -> "north"; case 1 -> "east"; case 2 -> "south"; default -> "west";
            };
        }

        public Bounds toWorld(Bounds local) {
            Point a = toWorld(local.min);
            Point b = toWorld(new Point(local.min.x, local.min.y, local.max.z));
            Point c = toWorld(new Point(local.max.x, local.min.y, local.min.z));
            Point d = toWorld(local.max);
            return new Bounds(new Point(Math.min(Math.min(a.x, b.x), Math.min(c.x, d.x)),
                    y + local.min.y, Math.min(Math.min(a.z, b.z), Math.min(c.z, d.z))),
                    new Point(Math.max(Math.max(a.x, b.x), Math.max(c.x, d.x)),
                    y + local.max.y, Math.max(Math.max(a.z, b.z), Math.max(c.z, d.z))));
        }
    }
}
