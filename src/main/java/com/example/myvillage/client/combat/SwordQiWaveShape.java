package com.example.myvillage.client.combat;

import java.util.ArrayList;
import java.util.List;

/** Presentation-only mesh in blade-local coordinates: x spans width, y thickness, z travel. */
final class SwordQiWaveShape {
    private static final Profile PROFILE = Profile.load();
    static final int SEGMENTS = PROFILE.segments();
    private record Band(double depth, double thickness, int red, int green, int blue, int alpha) { }
    private record Wisp(double start, double end, double depth, double height, int alpha) { }
    private record Profile(int segments, double curvature, List<Band> bands, List<Wisp> wisps, double wispThickness) {
        static Profile load() {
            try (var in = SwordQiWaveShape.class.getResourceAsStream("/assets/myvillage/combat/sword_qi_wave.json")) {
                if (in == null) throw new IllegalStateException("Missing sword qi presentation profile");
                var root = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in,
                        java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                var bands = new ArrayList<Band>();
                for (var e : root.getAsJsonArray("bands")) {
                    var b = e.getAsJsonObject(); var c = b.getAsJsonArray("color");
                    bands.add(new Band(b.get("depth").getAsDouble(), b.get("thickness").getAsDouble(),
                            c.get(0).getAsInt(), c.get(1).getAsInt(), c.get(2).getAsInt(), c.get(3).getAsInt()));
                }
                var wisps = new ArrayList<Wisp>();
                for (var e : root.getAsJsonArray("wisps")) {
                    var w = e.getAsJsonObject();
                    wisps.add(new Wisp(w.get("start").getAsDouble(), w.get("end").getAsDouble(),
                            w.get("depth").getAsDouble(), w.get("height").getAsDouble(), w.get("alpha").getAsInt()));
                }
                var p = new Profile(root.get("segments").getAsInt(), root.get("curvature").getAsDouble(),
                        List.copyOf(bands), List.copyOf(wisps), root.get("wisp_half_thickness").getAsDouble());
                p.validate(); return p;
            } catch (java.io.IOException e) { throw new IllegalStateException(e); }
        }
        private void validate() {
            if (segments < 8 || segments > 128 || !(curvature > 0 && curvature <= 1)
                    || bands.size() < 2 || bands.size() > 8 || wisps.size() > 8
                    || !(wispThickness > 0 && wispThickness <= .1)) throw new IllegalArgumentException("Wave profile limits");
            double previous = -1;
            for (var b : bands) {
                if (!(b.depth() >= 0 && b.depth() <= curvature) || b.depth() < previous
                        || !(b.thickness() >= 0 && b.thickness() <= .2)
                        || b.red() < 0 || b.red() > 255 || b.green() < 0 || b.green() > 255
                        || b.blue() < 0 || b.blue() > 255 || b.alpha() < 0 || b.alpha() > 255)
                    throw new IllegalArgumentException("Wave band bounds");
                previous = b.depth();
            }
            for (var w : wisps) if (!(w.start() >= -1 && w.end() <= 1 && w.start() < w.end())
                    || !(w.depth() > previous && w.depth() <= curvature) || !(Math.abs(w.height()) + wispThickness <= 1)
                    || w.alpha() < 0 || w.alpha() > 100) throw new IllegalArgumentException("Wave wisp bounds");
        }
    }
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
        var quads = new ArrayList<Quad>();
        for (int i = 0; i < SEGMENTS; i++) {
            double a = -1 + 2.0 * i / SEGMENTS, b = -1 + 2.0 * (i + 1) / SEGMENTS;
            // A narrow bright leading rim and open, fading ribbons. No swollen solid body.
            quads.add(new Quad(point(a, 0, 1, halfWidth, halfHeight), point(b, 0, 1, halfWidth, halfHeight),
                    point(b, 0, -1, halfWidth, halfHeight), point(a, 0, -1, halfWidth, halfHeight)));
            for (int side : new int[]{-1, 1}) {
                for (int band = 0; band < PROFILE.bands().size() - 1; band++) {
                    quads.add(new Quad(point(a, band, side, halfWidth, halfHeight),
                            point(b, band, side, halfWidth, halfHeight),
                            point(b, band + 1, side, halfWidth, halfHeight),
                            point(a, band + 1, side, halfWidth, halfHeight)));
                }
            }
        }
        for (var w : PROFILE.wisps()) {
            for (int i = 0; i < SEGMENTS; i++) {
                double a = (double) i / SEGMENTS, b = (double) (i + 1) / SEGMENTS;
                quads.add(new Quad(wisp(w, a, -1, halfWidth, halfHeight), wisp(w, b, -1, halfWidth, halfHeight),
                        wisp(w, b, 1, halfWidth, halfHeight), wisp(w, a, 1, halfWidth, halfHeight)));
            }
        }
        return List.copyOf(quads);
    }

    private static Vertex point(double t, int band, int side, double width, double height) {
        var p = PROFILE.bands().get(band);
        double taper = Math.max(0, 1 - t * t);
        return new Vertex(t * width, side * height * p.thickness() * taper,
                -width * (PROFILE.curvature() * t * t + p.depth() * taper),
                p.red(), p.green(), p.blue(), p.alpha());
    }

    private static Vertex wisp(Wisp w, double u, int side, double width, double height) {
        double t = w.start() + (w.end() - w.start()) * u;
        double taper = Math.max(0, 1 - t * t), fade = Math.sin(Math.PI * u);
        var color = PROFILE.bands().get(PROFILE.bands().size() - 2);
        return new Vertex(t * width, height * (w.height() + side * PROFILE.wispThickness() * fade) * taper,
                -width * (PROFILE.curvature() * t * t + w.depth() * taper),
                color.red(), color.green(), color.blue(), (int) Math.round(w.alpha() * fade));
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
