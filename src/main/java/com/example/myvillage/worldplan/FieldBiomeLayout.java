package com.example.myvillage.worldplan;

import com.example.myvillage.worldplan.FieldBiomeRules.Domain;
import com.example.myvillage.worldplan.FieldBiomeRules.Kind;
import com.example.myvillage.worldplan.mine.StarterMineGeneration;
import com.example.myvillage.worldplan.roads.RoadLayout;
import com.example.myvillage.worldplan.wonder.FieldEncounterSites;
import com.example.myvillage.worldplan.wonder.FoldedRidge;
import com.example.myvillage.worldplan.wonder.LotusMountain;
import com.example.myvillage.wilderness.habitat.HabitatLayout;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.List;

/** Seeded biome field over the provincial ground, blended around fixed infrastructure. */
public final class FieldBiomeLayout {
    private record Rect(int minX, int minZ, int maxX, int maxZ) {
        int distance(int x, int z) {
            return Math.max(Math.max(0, Math.max(minX-x, x-maxX)), Math.max(minZ-z, z-maxZ));
        }
    }

    private record EncounterColumn(int height, Domain biome) {}
    public record Column(int height, Domain encounter, ProvincialBiomeRules.Profile provincial, int liquidY) {
        public String biome() {
            return provincial != null ? provincial.biome() : encounter == null ? null : encounter.biome();
        }
        public boolean lava() {
            return provincial != null && provincial.terrain().fluid() == ProvincialBiomeRules.Fluid.LAVA;
        }
    }

    private final WorldPlan world;
    private final FieldBiomeRules rules;
    private final FieldBiomeTerrain terrain;
    private final RoadLayout roads;
    private final List<Rect> foundations;
    private final ProvincialBiomeLayout provincial;

    public FieldBiomeLayout(WorldPlan world, RoadLayout roads, StarterMineGeneration mines, HabitatLayout habitats) {
        this.world = world;
        this.roads = roads;
        rules = world.rules().fieldBiomes;
        terrain = new FieldBiomeTerrain(world.seed(), world.rules().seaLevel, rules);
        provincial = null;
        var shapes = new ArrayList<Rect>();
        for (var r : world.graph().geometry().reservations())
            shapes.add(new Rect(r.minX(), r.minZ(), r.maxX(), r.maxZ()));
        var definition = mines.definition();
        mines.layout().ifPresent(m -> shapes.add(new Rect(m.minX(definition), m.minZ(definition),
                m.maxX(definition), m.maxZ(definition))));
        for (var m : mines.regional())
            shapes.add(new Rect(m.minX(definition), m.minZ(definition), m.maxX(definition), m.maxZ(definition)));
        int margin = habitats.rules().integer("site_radius");
        for (var site : habitats.sites())
            shapes.add(new Rect(site.x()-margin, site.z()-margin, site.x()+margin, site.z()+margin));
        foundations = List.copyOf(shapes);
    }

    public FieldBiomeTerrain terrain() { return terrain; }
    public WorldPlan world() { return world; }
    public List<ProvincialBiomeLayout.Site> provincialSites() { return provincial == null ? List.of() : provincial.sites(); }

    private FieldBiomeLayout(FieldBiomeLayout base, ProvincialBiomeLayout provincial) {
        world = base.world;
        rules = base.rules;
        terrain = base.terrain;
        roads = base.roads;
        foundations = base.foundations;
        this.provincial = provincial;
    }

    public FieldBiomeLayout withProvincial(List<FieldEncounterSites.Site> sites, LotusMountain lotus, FoldedRidge ridge) {
        if (world.rules().provincialBiomes == null) return this;
        return new FieldBiomeLayout(this, new ProvincialBiomeLayout(this, sites, lotus, ridge));
    }

    public int height(int x, int z) {
        return surface(x, z).height();
    }

    public Column surface(int x, int z) {
        var region = world.coreRegionAt(x,z).orElse(null);
        return surface(x, z, region == null ? "" : region.id(), world.surfaceY(x,z));
    }

    private Column surface(int x, int z, String region, int height) {
        var original = encounterColumn(x, z, rules.region(region), height);
        if (provincial == null) return new Column(original.height(), original.biome(), null, world.rules().seaLevel);
        var field = provincial.surface(region, x, z, original.height());
        return new Column(field.height(), field.profile() == null ? original.biome() : null, field.profile(), field.liquidY());
    }

    private Domain domainAt(int x, int z) {
        var region = world.coreRegionAt(x,z).orElse(null);
        return region == null ? null : rules.region(region.id());
    }

    public boolean owns(Domain domain, int x, int z) {
        return domainAt(x,z) == domain && influence(domain,x,z) > domain.biomeWeight();
    }

    public WorldPlan.Context apply(WorldPlan.Context base, int x, int z) {
        if (!base.core()) return base;
        var column = surface(x, z, base.regionId(), base.surfaceY());
        if (column.biome() == null && column.height() == base.surfaceY()) return base;
        String biome = column.biome() == null ? world.biomeAtSurface(world.rules().profile(base.profileId()),x,z,column.height())
                : column.biome();
        return new WorldPlan.Context(base.regionId(),base.displayName(),base.profileId(),base.core(),base.tier(),
                base.qi(),base.danger(),column.height(),biome);
    }

    private EncounterColumn encounterColumn(int x, int z, Domain domain, int base) {
        double weight = domain == null ? 0 : influence(domain,x,z);
        if (weight == 0) return new EncounterColumn(base,null);
        int height = (int)Math.floor(base+(terrain.height(domain,x,z)-base)*weight);
        boolean deep = domain.kind() == Kind.TIDE && height < world.rules().seaLevel-rules.tide.maximumBiomeDepth();
        return new EncounterColumn(height,weight > domain.biomeWeight() && !deep ? domain : null);
    }

    private double influence(Domain domain, int x, int z) {
        double patch = terrain.weight(domain,x,z);
        return patch == 0 ? 0 : patch * clearance(domain.region(), x, z);
    }

    double clearance(String regionId, int x, int z) {
        int clear = rules.foundationClearance, reach = clear+rules.foundationBlend;
        int nearest = reach;
        for (var foundation : foundations) {
            nearest = Math.min(nearest,foundation.distance(x,z));
            if (nearest <= clear) return 0;
        }
        // Reserve the immediate terrain around each chunk containing a cross-province or sect road.
        for (int cz=(z-reach)>>4;cz<=(z+reach)>>4;cz++) for (int cx=(x-reach)>>4;cx<=(x+reach)>>4;cx++) {
            if (roads.slice(ChunkPos.asLong(cx,cz)) == null) continue;
            int west = cx<<4, north = cz<<4;
            int distance = Math.max(Math.max(west-x,x-west-15),Math.max(north-z,z-north-15));
            nearest = Math.min(nearest,distance);
            if (nearest <= clear) return 0;
        }
        double outerDistance = world.rules().coreRadius - StrictMath.hypot(x,z);
        double[] warped = world.geometry().warp(x,z);
        double own = Double.POSITIVE_INFINITY, neighbouring = Double.POSITIVE_INFINITY;
        for (var region : world.regions()) {
            double dx = warped[0]-region.x(), dz = warped[1]-region.z();
            double distance = dx*dx+dz*dz;
            if (region.id().equals(regionId)) own = distance;
            else neighbouring = Math.min(neighbouring,distance);
        }
        double borderDistance = Math.min(outerDistance,(Math.sqrt(neighbouring)-Math.sqrt(own))*.5);
        return ease(nearest-clear,rules.foundationBlend) * ease(borderDistance,reach);
    }

    private static double ease(double distance, int width) {
        double t = Math.clamp(distance/width,0,1);
        return t*t*(3-2*t);
    }
}
