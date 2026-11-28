package com.example.myvillage.worldplan;

import com.example.myvillage.worldplan.FieldBiomeRules.Domain;
import com.example.myvillage.worldplan.FieldBiomeRules.Kind;
import java.util.ArrayList;
import java.util.List;

import static com.example.myvillage.region.runtime.RegionGeometry.hash;
import static com.example.myvillage.region.runtime.RegionGeometry.noise;
import static com.example.myvillage.region.runtime.RegionGeometry.unit;

/** Seeded coastal drainage and wind erosion. Selecting a boss site never writes into the field. */
public final class FieldBiomeTerrain {
    public record Point(double x, double z) {}
    public record Bay(int lane, int row, int side, double x, int branches, double spread, double phase,
                      double cut, double width, double bend) {}
    public record Fracture(int lane, int row, double z, double inset, double width, double depth,
                           double length, double twist) {}
    private record Shore(int row, int side, double offset) {}

    private final long seed;
    private final int sea;
    private final FieldBiomeRules rules;

    public FieldBiomeTerrain(long seed, int sea, FieldBiomeRules rules) {
        this.seed = seed;
        this.sea = sea;
        this.rules = rules;
    }

    public double weight(Domain domain, double x, double z) {
        double n = noise(seed ^ 0xc61c45fee99L, x / domain.patchScale(), z / domain.patchScale());
        return ease(domain.patchEdge(), domain.patchEdge() + domain.patchBlend(), n);
    }

    public double height(Domain domain, double x, double z) {
        return domain.kind() == Kind.TIDE ? tidal(x, z) : wind(x, z);
    }

    private double shoreDrift(double x) {
        return 146 * noise(seed + 261, x / 790, .52) + 71 * noise(seed + 262, x / 322, .27);
    }

    private double shoreWidth(double x) {
        return rules.tide.coastWidth() + 58 * noise(seed + 266, x / 573, 1.15);
    }

    private Shore coast(double x, double z) {
        double warped = z - shoreDrift(x);
        int row = (int)Math.round(warped / rules.tide.shorePeriod());
        double local = warped - (double)row * rules.tide.shorePeriod();
        return new Shore(row, local < 0 ? -1 : 1, Math.abs(local) - shoreWidth(x));
    }

    public Point bayPoint(Bay bay, double along, double outward) {
        double x = bay.x + along;
        return new Point(x, shoreDrift(x) + (double)bay.row * rules.tide.shorePeriod()
                + bay.side * (shoreWidth(x) + outward));
    }

    private double random(int i, int j, int stream) { return unit(hash(seed, i, j, stream)); }

    private Bay bay(int lane, int row, int side) {
        int j = row * 3 + side;
        return new Bay(lane, row, side,
                (lane + .5) * rules.tide.baySpacing() + (random(lane, j, 270) - .5) * 116,
                2 + (int)(random(lane, j, 271) * 3),
                rules.tide.channelSpread() + 16 * random(lane, j, 273),
                random(lane, j, 279) * (2 * StrictMath.PI),
                rules.tide.channelCut() + 5 * random(lane, j, 274),
                rules.tide.channelWidth() + 5 * random(lane, j, 287), (random(lane, j, 288) - .5) * .29);
    }

    public double branch(Bay bay, int index, double outward) {
        double spread = bay.spread * (.89 + .19 * ease(-101, -43, outward))
                * (1 - .55 * ease(-38, 121, outward));
        double bend = StrictMath.sin(outward / 49 + bay.phase + index * 1.23) * (13 + index * 1.7);
        return (index - (bay.branches - 1) * .5) * spread + bend + outward * bay.bend;
    }

    private double tidal(double x, double z) {
        var coast = coast(x, z);
        double outward = coast.offset;
        double sand = noise(seed + 298, x / 71, z / 61);
        double grit = noise(seed + 301, x / 22, z / 25);
        double shale = noise(seed + 319, x / 142, z / 161);
        double solid = ease(114, 248, -outward);
        double rock = solid * (rules.tide.rockLift() + 43 * ease(-.6, .74, shale)) + 4 * Math.max(0, grit);
        double channel = 0, basin = 0;
        if (outward > -127 && outward < 190) {
            int lane = (int)Math.floor(x / rules.tide.baySpacing());
            double taper = ease(-127, -63, outward) * (1 - ease(135, 190, outward));
            for (int i = lane - 1; i <= lane + 1; i++) {
                Bay bay = bay(i, coast.row, coast.side);
                double across = x - bay.x;
                if (Math.abs(across) > 171) continue;
                basin = Math.max(basin, 3.7 * bump(across / 96, (outward - 22) / 95));
                double width = bay.width + 5 * ease(5, 100, outward);
                for (int j = 0; j < bay.branches; j++) {
                    double distance = Math.abs(across - branch(bay, j, outward) + 3.2 * sand);
                    channel = Math.max(channel, bay.cut * taper * (1 - ease(2, width, distance)));
                }
            }
        }
        double layer = StrictMath.sin((outward + .33 * x + 17 * shale) / 9) * solid * 3.2;
        return sea - .085 * Math.clamp(outward, -181, 246) + rock + layer + sand * 2.8 + grit * .84
                - channel - basin;
    }

    /** First air above the temporary run-up; outside this bay the ambient sea is untouched. */
    public int tideTop(Bay bay, int x, int z) {
        var coast = coast(x, z);
        if (coast.row != bay.row || coast.side != bay.side) return sea;
        double d = coast.offset;
        double drift = Math.abs(x - bay.x - branch(bay, 0, d));
        double influence = (1 - ease(15, 78, drift)) * ease(-117, -53, d) * (1 - ease(32, 114, d));
        return sea + (int)Math.round(rules.tide.domain().maximumRise() * influence);
    }

    private double windDrift(double z) {
        return 96 * noise(seed + 326, z / 291, .6) + 87 * noise(seed + 329, z / 630, .2);
    }

    private Fracture fracture(int lane, int row) {
        return new Fracture(lane, row, row * (double)rules.wind.fractureSpacing() + (random(lane, row, 344) - .5) * 156,
                (57 + 27 * random(lane, row, 345)) * (random(lane, row, 349) > .5 ? 1 : -1),
                5 + 1.1 * random(lane, row, 352), rules.wind.fractureDepth() + 4 * random(lane, row, 353),
                54 + 47 * random(lane, row, 355), (random(lane, row, 358) - .5) * .21);
    }

    public double valleyCenter(Fracture fault, double z) {
        return fault.lane * (double)rules.wind.valleySpacing() + windDrift(z);
    }

    public double fractureX(Fracture fault, double z) {
        double ahead = z - fault.z;
        return valleyCenter(fault, z) + fault.inset + ahead * fault.twist + 3 * StrictMath.sin(ahead / 19);
    }

    private double wind(double x, double z) {
        double offset = x - windDrift(z);
        int lane = (int)Math.round(offset / rules.wind.valleySpacing());
        int row = (int)Math.round(z / rules.wind.fractureSpacing());
        double across = offset - lane * (double)rules.wind.valleySpacing();
        double side = Math.abs(across);
        double coarse = noise(seed + 376, x / 126, z / 122);
        double sand = noise(seed + 378, x / 54, z / 63);
        double grain = noise(seed + 379, x / 19, z / 22);
        double valley = rules.wind.valleyWidth() + 18 * coarse + 17 * sand;
        double shoulder = ease(valley, valley + 85, side);
        double ribs = ease(.08, .76, .5 + .5 * StrictMath.sin((z + .29 * x + 57 * coarse) / 38));
        double ledges = StrictMath.sin((x * .54 + z * .11 + 8 * sand) / 7.1);
        double rock = shoulder * (rules.wind.plateauLift() + 45 * ribs + 23 * coarse + ledges * 5.9);
        double wash = 1 - ease(4, 25, Math.abs(across + 24 * StrictMath.sin(z / 87)));
        var fault = fracture(lane, row);
        double distance = Math.abs(x - fractureX(fault, z));
        double cleft = fault.depth * (1 - ease(1.75, fault.width, distance))
                * (1 - ease(fault.length / 3, fault.length, Math.abs(z - fault.z)));
        return sea + rules.wind.heightAboveSea() + 5.5 * coarse + 4.4 * sand + .7 * grain
                - 2.3 * wash + 5 * ease(20, 108, side) + rock - cleft;
    }

    public List<Bay> bays(int x, int z, int radius) {
        List<Bay> result = new ArrayList<>();
        int spacing = rules.tide.baySpacing(), period = rules.tide.shorePeriod();
        for (int row = Math.floorDiv(z - radius, period) - 1; row <= Math.floorDiv(z + radius, period) + 1; row++) {
            for (int lane = Math.floorDiv(x - radius, spacing); lane <= Math.floorDiv(x + radius, spacing); lane++) {
                for (int side : new int[]{-1, 1}) {
                    Bay bay = bay(lane, row, side);
                    if (bay.branches == 3 && within(bayPoint(bay, 0, -57), x, z, radius)) result.add(bay);
                }
            }
        }
        return result;
    }

    public List<Fracture> fractures(int x, int z, int radius) {
        List<Fracture> result = new ArrayList<>();
        int vertical = rules.wind.fractureSpacing(), spacing = rules.wind.valleySpacing();
        for (int row = Math.floorDiv(z - radius, vertical); row <= Math.floorDiv(z + radius, vertical) + 1; row++) {
            for (int lane = Math.floorDiv(x - radius, spacing) - 1; lane <= Math.floorDiv(x + radius, spacing) + 1; lane++) {
                Fracture fault = fracture(lane, row);
                if (within(new Point(valleyCenter(fault, fault.z), fault.z), x, z, radius)) result.add(fault);
            }
        }
        return result;
    }

    private static boolean within(Point p, int x, int z, int radius) {
        return Math.abs(p.x - x) <= radius && Math.abs(p.z - z) <= radius;
    }

    private static double bump(double x, double z) { return StrictMath.exp(-(x * x + z * z)); }
    private static double ease(double lo, double hi, double value) {
        double t = Math.clamp((value - lo) / (hi - lo), 0, 1);
        return t * t * (3 - 2 * t);
    }
}
