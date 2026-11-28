package com.example.myvillage.worldplan.wonder;

import com.example.myvillage.wilderness.habitat.HabitatLayout;
import com.example.myvillage.worldplan.FieldBiomeRules.Domain;
import com.example.myvillage.worldplan.FieldBiomeRules.Kind;
import com.example.myvillage.worldplan.FieldBiomeTerrain;
import com.example.myvillage.worldplan.FieldBiomeLayout;
import com.example.myvillage.worldplan.FieldBiomeTerrain.Bay;
import com.example.myvillage.worldplan.FieldBiomeTerrain.Fracture;
import com.example.myvillage.worldplan.FieldBiomeTerrain.Point;
import com.example.myvillage.worldplan.WorldPlan;
import com.example.myvillage.worldplan.mine.StarterMineGeneration;
import com.example.myvillage.worldplan.roads.RoadLayout;
import com.example.myvillage.worldplan.roads.RoadRules;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** A deterministic survey over the real provincial surface. A site contains no terrain template. */
public final class FieldEncounterSites {
    private static final int STEP = 4;
    private static final int[] DX = {1, 0, -1, 0}, DZ = {0, 1, 0, -1};

    public record Site(Domain domain, BoundingBox bounds, BlockPos home, List<BlockPos> exits,
                       BlockPos marker, Bay bay, BlockPos exposedShoal, BlockPos bridgeA, BlockPos bridgeB, int dryArea) {
        public boolean contains(int x, int z) {
            return x >= bounds.minX() && x <= bounds.maxX() && z >= bounds.minZ() && z <= bounds.maxZ();
        }
    }

    private final WorldPlan world;
    private final FieldBiomeLayout field;
    private final FieldBiomeTerrain terrain;
    private final RoadLayout roads;
    private final RoadRules roadRules;
    private final StarterMineGeneration mines;
    private final HabitatLayout habitats;
    private final LotusMountain lotus;
    private final FoldedRidge ridge;
    private final int minY, maxY;

    private FieldEncounterSites(FieldBiomeLayout field, RoadLayout roads, RoadRules roadRules, StarterMineGeneration mines,
                                HabitatLayout habitats, LotusMountain lotus, FoldedRidge ridge, int minY, int maxY) {
        this.field=field; world=field.world(); terrain=field.terrain(); this.roads=roads; this.roadRules=roadRules;
        this.mines=mines; this.habitats=habitats; this.lotus=lotus; this.ridge=ridge;
        this.minY=minY; this.maxY=maxY;
    }

    public static Map<Kind, Site> plan(FieldBiomeLayout field, RoadLayout roads, RoadRules roadRules, StarterMineGeneration mines,
                                      HabitatLayout habitats, LotusMountain lotus, FoldedRidge ridge, int minY, int maxY) {
        if (field==null) return Map.of();
        var planner = new FieldEncounterSites(field, roads, roadRules, mines, habitats, lotus, ridge, minY, maxY);
        var sites = new EnumMap<Kind, Site>(Kind.class);
        for (var domain : field.world().rules().fieldBiomes.domains) {
            Site site = planner.find(domain);
            if (site != null) sites.put(domain.kind(), site);
        }
        return Map.copyOf(sites);
    }

    private Site find(Domain domain) {
        var region = world.regions().stream().filter(r -> r.id().equals(domain.region())).findFirst().orElseThrow();
        double[] center = world.geometry().unwarp(region.x(), region.z());
        int x=(int)Math.round(center[0]), z=(int)Math.round(center[1]);
        if (domain.kind() == Kind.TIDE) {
            var candidates = terrain.bays(x, z, domain.surveyRadius());
            candidates.sort(Comparator.comparingDouble(b -> distance(terrain.bayPoint(b,0,-57),x,z)));
            for (Bay bay : candidates) {
                var focus = terrain.bayPoint(bay, 0, -57);
                BoundingBox bounds = footprint(domain, focus);
                if (!legal(domain, bounds)) continue;
                Site site = tide(domain, bounds, bay);
                if (site != null) return site;
            }
        } else {
            var candidates = terrain.fractures(x, z, domain.surveyRadius());
            candidates.sort(Comparator.comparingDouble(f -> distance(centroid(f),x,z)));
            for (Fracture fault : candidates) {
                BoundingBox bounds = footprint(domain, centroid(fault));
                if (!legal(domain, bounds)) continue;
                Site site = wind(domain, bounds, fault);
                if (site != null) return site;
            }
        }
        return null;
    }

    private Point centroid(Fracture fault) { return new Point(terrain.valleyCenter(fault,fault.z()),fault.z()+12); }
    private static double distance(Point point, int x, int z) {
        return Math.pow(point.x()-x,2) + Math.pow(point.z()-z,2);
    }

    private BoundingBox footprint(Domain domain, Point focus) {
        int x = (int)Math.round(focus.x())-domain.arenaWidth()/2;
        int z = (int)Math.round(focus.z())-domain.arenaDepth()/2;
        return new BoundingBox(x,minY,z,x+domain.arenaWidth()-1,maxY-1,z+domain.arenaDepth()-1);
    }

    private boolean legal(Domain domain, BoundingBox box) {
        int margin=domain.clearance(), x0=box.minX()-margin,z0=box.minZ()-margin;
        int x1=box.maxX()+margin,z1=box.maxZ()+margin;
        for (int x : new int[]{x0,(x0+x1)/2,x1}) for (int z : new int[]{z0,(z0+z1)/2,z1}) {
            if (!field.owns(domain, x, z)) return false;
        }
        if (world.graph().geometry().reservations().stream().anyMatch(r ->
                x0<=r.maxX() && x1>=r.minX() && z0<=r.maxZ() && z1>=r.minZ())) return false;
        if (habitats.sites().stream().anyMatch(s -> {
            int m=habitats.rules().integer("site_radius");
            return x0<=s.x()+m && x1>=s.x()-m && z0<=s.z()+m && z1>=s.z()-m;
        })) return false;
        return (lotus==null || !lotus.intersects(x0,z0,x1,z1))
                && (ridge==null || !ridge.intersects(x0,z0,x1,z1))
                && !roads.intersects(world,roadRules,x0,minY,z0,x1,maxY-1,z1)
                && !mines.intersects(x0,minY,z0,x1,maxY-1,z1);
    }

    private Site tide(Domain domain, BoundingBox box, Bay bay) {
        var survey = new Survey(box,bay);
        if (!survey.forks(bay)) return null;
        int start=survey.near(terrain.bayPoint(bay,0,-102), 39, i -> survey.dry(i,true));
        if (start<0) return null;
        var connected=survey.reach(start,i -> survey.dry(i,true),2);
        if (connected.area<domain.dryArea()) return null;
        int west=survey.near(terrain.bayPoint(bay,-102,-101),36,i -> connected.seen[i]);
        int east=survey.near(terrain.bayPoint(bay,102,-101),36,i -> connected.seen[i]);
        int sluice=survey.near(terrain.bayPoint(bay,terrain.branch(bay,0,-60)+22,-60),30,
                i -> connected.seen[i] && survey.wetNeighbour(i));
        var low=survey.reach(start,i -> survey.dry(i,false),2);
        int shoal=survey.near(terrain.bayPoint(bay,terrain.branch(bay,0,-31)+21,-31),46,
                i -> low.seen[i] && survey.dry(i,false) && !survey.dry(i,true));
        int home=survey.near(terrain.bayPoint(bay,-25,-83),31,i -> connected.seen[i] && survey.footprint(i,2));
        if (west<0 || east<0 || sluice<0 || shoal<0 || home<0) return null;
        return new Site(domain,box,survey.spawn(home,2),List.of(survey.at(west),survey.at(east)),
                survey.at(sluice),bay,survey.at(shoal),null,null,connected.area);
    }

    private Site wind(Domain domain, BoundingBox box, Fracture fault) {
        var survey = new Survey(box,null);
        int start=survey.near(centroid(fault),41,i -> survey.dry(i,false) && survey.footprint(i,3));
        if (start<0) return null;
        var connected=survey.reach(start,i -> survey.dry(i,false),2);
        if (connected.area<domain.dryArea()) return null;
        var center=centroid(fault);
        int west=survey.near(new Point(center.x()-99,center.z()),32,i -> connected.seen[i]);
        int east=survey.near(new Point(center.x()+99,center.z()),32,i -> connected.seen[i]);
        int exit=survey.near(new Point(center.x(),center.z()+87),28,i -> connected.seen[i]);
        if (west<0 || east<0 || exit<0) return null;
        for (int dz : new int[]{0, 3, -3, 7, -7, 11, -11, 15, -15}) {
            int z=(int)Math.round(fault.z())+dz, x=(int)Math.round(terrain.fractureX(fault,z));
            int half=world.rules().fieldBiomes.wind.bridgeSpan()/2;
            int a=x-half,b=x+half;
            int midY=field.height(x,z), aY=field.height(a,z), bY=field.height(b,z);
            if (Math.min(aY,bY)-midY < 7 || Math.abs(aY-bY)>2 || !bridgeBanks(a,aY,b,bY,z)) continue;
            int left=survey.near(new Point(a-11,z),12,i -> connected.seen[i] && survey.footprint(i,3)
                    && Math.abs(survey.height[i]-aY)<=4 && survey.x(i)<a-4);
            int right=survey.near(new Point(b+5,z),15,i -> connected.seen[i] && survey.x(i)>b);
            if (left<0 || right<0) continue;
            return new Site(domain,box,survey.spawn(left,3),List.of(survey.at(west),survey.at(east),survey.at(exit)),
                    survey.at(left),null,null,new BlockPos(a,aY,z),new BlockPos(b,bY,z),connected.area);
        }
        return null;
    }

    private boolean bridgeBanks(int x1, int y1, int x2, int y2, int z) {
        int width = com.example.myvillage.entity.beast.FieldEncounterRules.bundled().encounter().bridgeWidth() / 2;
        for (int dz=-width; dz<=width; dz++) {
            int start=field.height(x1,z+dz), end=field.height(x2,z+dz);
            if (start<y1 || start>y1+1 || end<y2 || end>y2+1) return false;
            for (int x=x1+1; x<x2; x++) {
                int deck=(int)Math.round(y1+(double)(x-x1)*(y2-y1)/(x2-x1));
                if (field.height(x,z+dz)>deck) return false;
            }
        }
        return true;
    }

    private interface CellRule { boolean test(int i); }
    private record Reach(boolean[] seen, int area) {}

    private final class Survey {
        final int width, depth, x0, z0;
        final int sea=world.rules().seaLevel;
        final int[] height, high;
        Survey(BoundingBox box, Bay bay) {
            x0=box.minX()+STEP/2; z0=box.minZ()+STEP/2;
            width=box.getXSpan()/STEP; depth=box.getZSpan()/STEP;
            height=new int[width*depth]; high=new int[height.length];
            for (int i=0;i<height.length;i++) {
                height[i]=field.height(x(i),z(i));
                high[i]=bay==null ? sea : terrain.tideTop(bay,x(i),z(i));
            }
        }
        int x(int i){return x0+(i%width)*STEP;}
        int z(int i){return z0+(i/width)*STEP;}
        BlockPos at(int i){return new BlockPos(x(i),height[i],z(i));}
        boolean dry(int i, boolean raised){return height[i]>=(raised ? high[i] : sea);}
        boolean footprint(int i,int radius) {
            int sy=height[i];
            for (int dz=-radius;dz<=radius;dz++) for(int dx=-radius;dx<=radius;dx++)
                if (Math.abs(field.height(x(i)+dx,z(i)+dz)-sy)>1) return false;
            return true;
        }
        BlockPos spawn(int i,int radius) {
            int y=height[i];
            for (int dz=-radius;dz<=radius;dz++) for(int dx=-radius;dx<=radius;dx++)
                y=Math.max(y,field.height(x(i)+dx,z(i)+dz));
            return new BlockPos(x(i),y,z(i));
        }
        int near(Point p, int radius, CellRule test) {
            int result=-1; double best=radius*radius;
            for(int i=0;i<height.length;i++) {
                double d=distance(p,x(i),z(i));
                if(d<=best && test.test(i)){result=i;best=d;}
            }
            return result;
        }
        int adjacent(int i,int dir) {
            int u=i%width+DX[dir], v=i/width+DZ[dir];
            return u<0||u>=width||v<0||v>=depth ? -1 : v*width+u;
        }
        boolean wetNeighbour(int i) {
            for(int d=0;d<4;d++){ int n=adjacent(i,d); if(n>=0 && !dry(n,true))return true; }
            return false;
        }
        Reach reach(int begin,CellRule traversable,int grade) {
            boolean[] seen=new boolean[height.length];
            int[] queue=new int[height.length]; queue[0]=begin; seen[begin]=true;
            int count=1;
            for(int i=0;i<count;i++) {
                int current=queue[i];
                for(int d=0;d<4;d++) {
                    int n=adjacent(current,d);
                    if(n>=0 && !seen[n] && traversable.test(n)
                            && Math.abs(height[n]-height[current])<=grade) {
                        seen[n]=true;
                        queue[count++]=n;
                    }
                }
            }
            return new Reach(seen,count*STEP*STEP);
        }
        boolean forks(Bay bay) {
            int mouth=near(terrain.bayPoint(bay,0,47),32,i -> !dry(i,false));
            if (mouth<0) return false;
            var wet=reach(mouth,i -> !dry(i,false),Integer.MAX_VALUE);
            for (int d : new int[]{-63,-46,-35}) {
                int branches=0;
                for (int c=0;c<3;c++) {
                    if (near(terrain.bayPoint(bay,terrain.branch(bay,c,d),d),10,i->wet.seen[i])<0) break;
                    if (c>0) {
                        double bank=(terrain.branch(bay,c,d)+terrain.branch(bay,c-1,d))*.5;
                        if (near(terrain.bayPoint(bay,bank,d),8,i -> dry(i,false))<0) break;
                    }
                    branches++;
                }
                if (branches==3) return true;
            }
            return false;
        }
    }
}
