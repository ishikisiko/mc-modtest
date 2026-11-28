package com.example.myvillage.worldplan;

import com.example.myvillage.worldplan.FieldBiomeRules.Domain;
import com.example.myvillage.worldplan.FieldBiomeRules.Kind;
import com.example.myvillage.worldplan.mine.StarterMineGeneration;
import com.example.myvillage.worldplan.roads.RoadLayout;
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

    public record Column(int height, Domain biome) {}

    private final WorldPlan world;
    private final FieldBiomeRules rules;
    private final FieldBiomeTerrain terrain;
    private final RoadLayout roads;
    private final List<Rect> foundations;

    public FieldBiomeLayout(WorldPlan world, RoadLayout roads, StarterMineGeneration mines, HabitatLayout habitats) {
        this.world = world;
        this.roads = roads;
        rules = world.rules().fieldBiomes;
        terrain = new FieldBiomeTerrain(world.seed(), world.rules().seaLevel, rules);
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

    public int height(int x, int z) {
        return column(x,z,domainAt(x,z),world.surfaceY(x,z)).height();
    }

    public Domain biomeAt(int x, int z) {
        var domain = domainAt(x,z);
        return domain == null ? null : column(x,z,domain,world.surfaceY(x,z)).biome();
    }

    private Domain domainAt(int x, int z) {
        var region = world.coreRegionAt(x,z).orElse(null);
        return region == null ? null : rules.region(region.id());
    }

    public boolean owns(Domain domain, int x, int z) {
        return domainAt(x,z) == domain && influence(domain,x,z) > domain.biomeWeight();
    }

    public WorldPlan.Context apply(WorldPlan.Context base, int x, int z) {
        var domain = base.core() ? rules.region(base.regionId()) : null;
        if (domain == null) return base;
        var column = column(x,z,domain,base.surfaceY());
        if (column.biome() == null && column.height() == base.surfaceY()) return base;
        String biome = column.biome() == null ? world.biomeAtSurface(world.rules().profile(base.profileId()),x,z,column.height())
                : column.biome().biome();
        return new WorldPlan.Context(base.regionId(),base.displayName(),base.profileId(),base.core(),base.tier(),
                base.qi(),base.danger(),column.height(),biome);
    }

    private Column column(int x, int z, Domain domain, int base) {
        double weight = domain == null ? 0 : influence(domain,x,z);
        if (weight == 0) return new Column(base,null);
        int height = (int)Math.floor(base+(terrain.height(domain,x,z)-base)*weight);
        boolean deep = domain.kind() == Kind.TIDE && height < world.rules().seaLevel-rules.tide.maximumBiomeDepth();
        return new Column(height,weight > domain.biomeWeight() && !deep ? domain : null);
    }

    private double influence(Domain domain, int x, int z) {
        double patch = terrain.weight(domain,x,z);
        if (patch == 0) return 0;
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
            if (region.id().equals(domain.region())) own = distance;
            else neighbouring = Math.min(neighbouring,distance);
        }
        double borderDistance = Math.min(outerDistance,(Math.sqrt(neighbouring)-Math.sqrt(own))*.5);
        return patch * ease(nearest-clear,rules.foundationBlend) * ease(borderDistance,reach);
    }

    private static double ease(double distance, int width) {
        double t = Math.clamp(distance/width,0,1);
        return t*t*(3-2*t);
    }
}
