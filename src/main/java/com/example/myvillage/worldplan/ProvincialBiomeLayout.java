package com.example.myvillage.worldplan;

import com.example.myvillage.worldplan.ProvincialBiomeRules.Fluid;
import com.example.myvillage.worldplan.ProvincialBiomeRules.Profile;
import com.example.myvillage.worldplan.wonder.FieldEncounterSites;
import com.example.myvillage.worldplan.wonder.FoldedRidge;
import com.example.myvillage.worldplan.wonder.LotusMountain;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.example.myvillage.region.runtime.RegionGeometry.hash;
import static com.example.myvillage.region.runtime.RegionGeometry.noise;
import static com.example.myvillage.region.runtime.RegionGeometry.unit;

/** Coarse, seed-stable site selection followed by continuous landforms at block resolution. */
public final class ProvincialBiomeLayout {
    private static final int MAX_CANDIDATES = 18;
    private static final double[] SURVEY_OFFSETS = {-0.83, -0.42, 0, 0.42, 0.83};
    private final FieldBiomeLayout fields;
    private final WorldPlan world;
    private final ProvincialBiomeRules rules;
    private final List<FieldEncounterSites.Site> arenas;
    private final LotusMountain lotus;
    private final FoldedRidge ridge;
    private final int sea;
    private final Map<String, List<Site>> byRegion;

    public record Site(Profile profile, int x, int z, int base, double cosine, double sine, long salt) {}
    public record Surface(Profile profile, int height, int liquidY) {}
    private record Candidate(Site site, double score) {}
    private record Point(int x, int z, int height, double ruggedness, double direction) {}
    private record Local(double along, double across) {}
    private record Ground(double height, int liquidY) {}

    ProvincialBiomeLayout(FieldBiomeLayout fields, List<FieldEncounterSites.Site> arenas, LotusMountain lotus, FoldedRidge ridge) {
        this.fields = fields;
        this.world = fields.world();
        this.rules = world.rules().provincialBiomes;
        this.arenas = List.copyOf(arenas);
        this.lotus = lotus;
        this.ridge = ridge;
        this.sea = world.rules().seaLevel;
        byRegion = chooseSites();
    }

    public List<Site> sites() {
        return rules.profiles.stream().flatMap(p -> byRegion.getOrDefault(p.region(), List.of()).stream()
                .filter(site -> site.profile() == p)).toList();
    }

    Surface surface(String region, int x, int z, int base) {
        List<Site> sites = byRegion.get(region);
        if (sites == null) return new Surface(null, base, sea);
        Site winner = null;
        double score = 0, runnerUp = 0;
        for (Site site : sites) {
            double strength = envelope(site, x, z);
            if (strength > score) {
                runnerUp = score;
                score = strength;
                winner = site;
            } else runnerUp = Math.max(runnerUp, strength);
        }
        if (winner == null || score <= rules.vanillaWeight - .12) return new Surface(null, base, sea);
        score *= allowance(region, x, z);
        double mix = smooth(rules.vanillaWeight - .12, .94, score) * smooth(0, .26, score - runnerUp);
        if (mix <= 0) return new Surface(null, base, sea);
        Ground ground = ground(winner, x, z, base, mix);
        int height = Math.clamp((int)Math.floor(ground.height()), world.rules().minHeight, world.rules().maxHeight);
        return new Surface(score > rules.vanillaWeight ? winner.profile() : null, height, Math.max(sea, ground.liquidY()));
    }

    private Map<String, List<Site>> chooseSites() {
        Map<String, List<Point>> points = survey();
        Map<String, List<Site>> selections = new HashMap<>();
        for (var region : world.regions()) {
            var profiles = rules.in(region.id());
            if (profiles.isEmpty()) continue;
            var available = points.getOrDefault(region.id(), List.of());
            var first = candidates(profiles.getFirst(), available);
            if (profiles.size() == 1) {
                if (!first.isEmpty()) selections.put(region.id(), List.of(first.getFirst().site()));
                continue;
            }
            var second = candidates(profiles.getLast(), available);
            List<Site> best = List.of();
            double score = Double.NEGATIVE_INFINITY;
            for (Candidate a : first) for (Candidate b : second) {
                double distance = StrictMath.hypot(a.site().x() - b.site().x(), a.site().z() - b.site().z());
                double breadth = (a.site().profile().depth() + b.site().profile().depth()) * .42;
                if (distance < breadth * .83) continue;
                double pair = a.score() + b.score() + Math.min(1.65, distance / breadth) * 1.5;
                if (pair > score) {
                    score = pair;
                    best = List.of(a.site(), b.site());
                }
            }
            if (best.isEmpty()) {
                if (!first.isEmpty()) best = List.of(first.getFirst().site());
                else if (!second.isEmpty()) best = List.of(second.getFirst().site());
            }
            if (!best.isEmpty()) selections.put(region.id(), best);
        }
        return Map.copyOf(selections);
    }

    private Map<String, List<Point>> survey() {
        Map<String, List<Point>> result = new HashMap<>();
        int radius = world.rules().coreRadius, step = rules.surveyStep, span = 72;
        int from = -Math.floorDiv(radius, step) * step;
        for (int z = from; z <= radius; z += step) for (int x = from; x <= radius; x += step) {
            var region = world.coreRegionAt(x, z).orElse(null);
            if (region == null || rules.in(region.id()).isEmpty() || allowance(region.id(), x, z) < .97) continue;
            int y = fields.height(x, z);
            double dx = world.naturalTerrainY(x + span, z) - world.naturalTerrainY(x - span, z);
            double dz = world.naturalTerrainY(x, z + span) - world.naturalTerrainY(x, z - span);
            double slope = Math.clamp(StrictMath.hypot(dx, dz) / 37, 0, 1);
            result.computeIfAbsent(region.id(), ignored -> new ArrayList<>())
                    .add(new Point(x, z, y, slope, StrictMath.atan2(-dz, -dx)));
        }
        return result;
    }

    private List<Candidate> candidates(Profile profile, List<Point> points) {
        long salt = hash(world.seed(), profile.biome().hashCode(), profile.region().hashCode(), 281);
        var rank = points.stream().filter(p -> profile.terrain().fluid() == Fluid.SEA ? p.height() <= sea : p.height() >= sea - 5)
                .map(p -> candidate(profile, p, salt)).sorted(Comparator.comparingDouble(Candidate::score).reversed()).toList();
        var result = new ArrayList<Candidate>();
        var surveyed = new ArrayList<Site>();
        for (Candidate c : rank) {
            Site site = c.site();
            if (surveyed.stream().anyMatch(a -> StrictMath.hypot(a.x() - site.x(), a.z() - site.z()) < 355)) continue;
            surveyed.add(site);
            double extent = surveyFootprint(site);
            if (extent > .45) result.add(new Candidate(site, c.score() + extent * 7));
            if (result.size() >= MAX_CANDIDATES || surveyed.size() >= MAX_CANDIDATES * 5) break;
        }
        result.sort(Comparator.comparingDouble(Candidate::score).reversed());
        return result;
    }

    private Candidate candidate(Profile profile, Point p, long salt) {
        double rolled = unit(hash(salt, p.x(), p.z(), 59));
        double angle = switch (profile.terrain().form()) {
            case CHANNEL, TERRACE -> p.direction() + (rolled - .5) * .75;
            default -> rolled * 2 * StrictMath.PI;
        };
        double heightError = Math.abs(p.height() - sea - profile.altitude());
        double ruggedError = Math.abs(p.ruggedness() - profile.ruggedness());
        double local = .55 * noise(salt, p.x() / 730., p.z() / 730.);
        double preference = 2.8 / (1 + heightError / 28) + 1.0 - ruggedError + local;
        int reference = profile.terrain().fluid() == Fluid.SEA ? sea - 14 : Math.max(sea + 7, p.height());
        return new Candidate(new Site(profile, p.x(), p.z(), reference, StrictMath.cos(angle), StrictMath.sin(angle), salt), preference);
    }

    private double surveyFootprint(Site site) {
        double support = 0;
        int count = 0;
        for (double u : SURVEY_OFFSETS) for (double v : SURVEY_OFFSETS) {
            if (u * u + v * v > .96) continue;
            double along = u * site.profile().width() * .5, across = v * site.profile().depth() * .5;
            int x = site.x() + (int)Math.round(along * site.cosine() - across * site.sine());
            int z = site.z() + (int)Math.round(along * site.sine() + across * site.cosine());
            double room = allowance(site.profile().region(), x, z);
            if (room > .85 && site.profile().terrain().fluid() == Fluid.SEA && fields.height(x, z) > sea + 6) room *= .25;
            support += room;
            count++;
        }
        return support / count;
    }

    private double allowance(String region, int x, int z) {
        var owner = world.coreRegionAt(x, z).orElse(null);
        if (owner == null || !owner.id().equals(region)) return 0;
        double reserved = fields.clearance(region, x, z);
        if (reserved == 0) return 0;
        for (var site : arenas) {
            if (!site.domain().region().equals(region)) continue;
            var bounds = site.bounds();
            int distance = Math.max(Math.max(bounds.minX() - x, x - bounds.maxX()), Math.max(bounds.minZ() - z, z - bounds.maxZ()));
            reserved *= smooth(site.domain().clearance(), site.domain().clearance() + 94, distance);
        }
        if (lotus != null && (region.equals(world.rules().lotusMountain.region())))
            reserved *= landmarkMargin(x, z, lotus::intersects);
        if (ridge != null && (region.equals(world.rules().foldedRidge.region())))
            reserved *= landmarkMargin(x, z, ridge::intersects);
        return reserved;
    }

    @FunctionalInterface
    private interface Intersection { boolean test(int minX, int minZ, int maxX, int maxZ); }
    private static double landmarkMargin(int x, int z, Intersection intersects) {
        if (intersects.test(x - 16, z - 16, x + 16, z + 16)) return 0;
        if (!intersects.test(x - 86, z - 86, x + 86, z + 86)) return 1;
        int low = 16, high = 86;
        while (high - low > 3) {
            int mid = (low + high) / 2;
            if (intersects.test(x - mid, z - mid, x + mid, z + mid)) high = mid;
            else low = mid;
        }
        return smooth(16, 86, low);
    }

    private static double envelope(Site site, int x, int z) {
        if (Math.abs(x - site.x()) > site.profile().width() || Math.abs(z - site.z()) > site.profile().width()) return 0;
        Local p = local(site, x, z);
        double dx = p.along() / (site.profile().width() * .55);
        double dz = p.across() / (site.profile().depth() * .55);
        double bend = .17 * noise(site.salt(), x / 297., z / 297.) + .074 * noise(site.salt() + 213, x / 137., z / 149.);
        return 1 - smooth(.50, 1.28, StrictMath.hypot(dx, dz) + bend);
    }

    private Ground ground(Site site, int x, int z, int base, double mix) {
        var t = site.profile().terrain();
        long seed = site.salt();
        var p = local(site, x, z);
        double a = p.along(), b = p.across(), spacing = t.spacing();
        double broad = noise(seed + 263, a / (spacing * 1.9), b / (spacing * 1.4));
        double ridgeNoise = noise(seed + 294, a / spacing, b / (spacing * .54));
        double grain = noise(seed + 297, a / 44, b / 41);
        double carry = (base - site.base()) * .31;
        double height = site.base() + carry;
        int localWater = sea;
        double wet = 0;
        switch (t.form()) {
            case CHANNEL -> {
                double center = b + ridgeNoise * spacing * .22 + StrictMath.sin(a / (spacing * .72)) * spacing * .19;
                double channel = Math.abs(StrictMath.sin(center * StrictMath.PI / spacing));
                double cut = 1 - smooth(.045, .55, channel);
                double branch = Math.abs(StrictMath.sin((b + a * .31 + broad * spacing * .25) * StrictMath.PI / (spacing * 1.7)));
                cut = Math.max(cut, (1 - smooth(.025, .26, branch)) * .78 * smooth(-.42, .10, broad));
                height += broad * t.relief() * .9 + (1 - cut) * t.relief() * .30 - cut * t.cut();
                double grade = Math.clamp(-a / 58, -t.relief() * .4, t.relief() * .4);
                height += grade;
                wet = cut * smooth(-.64, -.04, ridgeNoise);
                localWater = Math.max(sea + 3, site.base() + (int)grade - Math.min(11, t.cut() / 3));
            }
            case HOLLOW -> {
                double basin = noise(seed + 831, a / spacing, b / (spacing * .68));
                wet = smooth(.10, .69, basin + broad * .24);
                height += broad * t.relief() + grain * 2 + t.relief() * .35 - wet * t.cut();
                localWater = Math.max(sea + 2, site.base() + (int)Math.floor(broad * t.relief()) - t.cut() / 5);
            }
            case TERRACE -> {
                double grade = Math.clamp(-a / 48, -32, 32);
                double undulation = grade + broad * t.relief() + ridgeNoise * 9;
                int step = Math.max(4, t.relief() / 4);
                double tread = Math.floor(undulation / step) * step;
                double face = smooth(.74, .98, (undulation - tread) / step);
                height += tread + step * face + grain * 1.9;
                wet = smooth(-.11, .53, ridgeNoise) * (1 - .48 * face);
                localWater = Math.max(sea + 3, site.base() + (int)tread + 1);
                if (t.fluid() == Fluid.WATER) height -= wet * t.cut();
            }
            case RIDGE -> {
                double spine = .5 + .5 * StrictMath.sin((b + broad * spacing * .3) / spacing * 2 * StrictMath.PI);
                height += broad * t.relief() + spine * (t.relief() * .52) + ridgeNoise * 5 + grain * 1.3;
            }
            case SHELF -> {
                double spine = .5 + .5 * StrictMath.sin((a + ridgeNoise * spacing * .44) / spacing * 2 * StrictMath.PI);
                height = Math.clamp(sea - 18 + spine * 10 + broad * 7 + ridgeNoise * 3 + grain, sea - 27, sea - 4);
            }
        }
        boolean localFluid = t.fluid() == Fluid.WATER || t.fluid() == Fluid.ICE || t.fluid() == Fluid.LAVA;
        if (localFluid && mix > .63) {
            // Banks remain above the local plane. Only the interior of the selected drainage can hold sources.
            double fill = smooth(.59, .73, wet) * smooth(.79, .95, mix);
            double level = localWater + 1;
            height = Math.max(height, level) * (1 - fill) + Math.min(height, level - 2) * fill;
            if (fill <= .94) localWater = sea;
        } else localWater = sea;
        double finalY = base + (height - base) * mix;
        if (localWater > sea && finalY >= localWater) localWater = sea;
        return new Ground(finalY, localWater);
    }

    private static Local local(Site site, int x, int z) {
        double dx = x - site.x(), dz = z - site.z();
        return new Local(dx * site.cosine() + dz * site.sine(), dz * site.cosine() - dx * site.sine());
    }

    private static double smooth(double low, double high, double value) {
        double t = Math.clamp((value - low) / (high - low), 0, 1);
        return t * t * (3 - 2 * t);
    }
}
