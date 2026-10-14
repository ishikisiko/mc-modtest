package com.example.myvillage.client.combat;

import java.util.ArrayList;
import java.util.List;

/** Presentation-only mesh in blade-local coordinates: x spans width, y thickness, z travel. */
final class SwordQiWaveShape {
    static final int SEGMENTS = 40;
    record Vertex(double x, double y, double z, int red, int green, int blue, int alpha) { }
    record Quad(Vertex a, Vertex b, Vertex c, Vertex d) { }
    record Axis(double x, double y, double z) { }
    record Frame(Axis right, Axis up, Axis forward) {
        Axis orient(Vertex v) {
            return new Axis(right.x * v.x + up.x * v.y + forward.x * v.z,
                    right.y * v.x + up.y * v.y + forward.y * v.z,
                    right.z * v.x + up.z * v.y + forward.z * v.z);
        }
    }
    private SwordQiWaveShape() { }

    static List<Quad> mesh(double halfWidth, double halfHeight) {
        if (!Double.isFinite(halfWidth) || !Double.isFinite(halfHeight) || halfWidth <= 0 || halfHeight <= 0) {
            throw new IllegalArgumentException("Finite positive blade dimensions required");
        }
        var quads = new ArrayList<Quad>(SEGMENTS * 8);
        for (int i = 0; i < SEGMENTS; i++) {
            double a = -1 + 2.0 * i / SEGMENTS, b = -1 + 2.0 * (i + 1) / SEGMENTS;
            // A single bright knife edge, followed by a thicker cyan ridge and a fading concave tail.
            quads.add(new Quad(point(a, 0, 1, halfWidth, halfHeight), point(b, 0, 1, halfWidth, halfHeight),
                    point(b, 0, -1, halfWidth, halfHeight), point(a, 0, -1, halfWidth, halfHeight)));
            for (int side : new int[]{-1, 1}) {
                for (int band = 0; band < 3; band++) {
                    quads.add(new Quad(point(a, band, side, halfWidth, halfHeight),
                            point(b, band, side, halfWidth, halfHeight),
                            point(b, band + 1, side, halfWidth, halfHeight),
                            point(a, band + 1, side, halfWidth, halfHeight)));
                }
            }
            quads.add(new Quad(point(a, 3, -1, halfWidth, halfHeight), point(b, 3, -1, halfWidth, halfHeight),
                    point(b, 3, 1, halfWidth, halfHeight), point(a, 3, 1, halfWidth, halfHeight)));
        }
        return List.copyOf(quads);
    }

    private static Vertex point(double t, int band, int side, double width, double height) {
        double taper = Math.max(0, 1 - t * t);
        double depth = switch (band) { case 0 -> 0; case 1 -> .035; case 2 -> .17; default -> .44; };
        double thickness = switch (band) { case 0 -> .18; case 1 -> .24; case 2 -> .90; default -> .08; };
        int r = switch (band) { case 0 -> 234; case 1 -> 181; case 2 -> 91; default -> 66; };
        int g = switch (band) { case 0 -> 255; case 1 -> 241; case 2 -> 209; default -> 164; };
        int b = switch (band) { case 0 -> 255; case 1 -> 243; case 2 -> 218; default -> 184; };
        int alpha = switch (band) { case 0 -> 235; case 1 -> 152; case 2 -> 112; default -> 0; };
        return new Vertex(t * width, side * height * thickness * taper,
                -width * (.70 * t * t + depth * taper), r, g, b, alpha);
    }

    static boolean continuous(long priorRevision, long revision, double priorUpdated, double now) {
        return priorRevision == revision && now >= priorUpdated && now - priorUpdated <= 4;
    }

    static Frame frame(double dx, double dy, double dz) {
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (!Double.isFinite(length) || length < 1e-12) throw new IllegalArgumentException("Finite direction required");
        dx /= length; dy /= length; dz /= length;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        double fallbackLength = Math.sqrt(dx * dx + dy * dy);
        Axis right = horizontal < 1e-4 ? new Axis(dy / fallbackLength, -dx / fallbackLength, 0)
                : new Axis(-dz / horizontal, 0, dx / horizontal);
        double ux = right.y * dz - right.z * dy, uy = right.z * dx - right.x * dz,
                uz = right.x * dy - right.y * dx;
        double upLength = Math.sqrt(ux * ux + uy * uy + uz * uz);
        return new Frame(right, new Axis(ux / upLength, uy / upLength, uz / upLength), new Axis(dx, dy, dz));
    }

    static double interpolationFraction(double age) {
        return Double.isFinite(age) ? Math.clamp(age, 0, 1) : 0;
    }
}
