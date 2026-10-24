package com.example.myvillage.worldplan;

import com.example.myvillage.region.runtime.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.*;
import static com.example.myvillage.region.runtime.RegionGeometry.*;

/** V1 world grammar and terrain field. No Minecraft, chunk reads, mutable RNG or unbounded cache. */
public final class WorldPlan {
    public record Point(double x,double z) {}
    public record Region(WorldPlanRules.Profile profile,int x,int z,int tier) {public String id(){return profile.id();}}
    public record Border(String a,String b,Point p,Point q,boolean passable,String separator,boolean gate) {
        public Point crossing(){return new Point((p.x+q.x)/2,(p.z+q.z)/2);}
        public String key(){return a+":"+b;}
    }
    public record City(int x,int z,int baseY,int width,int height,int depth,String host) {
        public boolean contains(int wx,int wz,int margin){return wx>=x-margin&&wx<x+width+margin&&wz>=z-margin&&wz<z+depth+margin;}
    }
    public record Spawn(int x,int y,int z,String regionId,boolean raisedIsland) {}
    public record Settlement(String id,String layout,String layoutHash,String host,int x,int offsetY,int z,int rotation,long siteSeed) {}
    public record Context(String regionId,String displayName,String profileId,boolean core,int tier,double qi,double danger,int surfaceY,String biome) {}
    private record GeoEdge(int a,int b,Point p,Point q) {}
    private record Values(double height,double qi,double danger) {}
    private record Outer(long gx,long gz,double x,double z,WorldPlanRules.Profile profile,int tier) {}
    private final long seed;
    private final WorldPlanRules rules;
    private final RegionGeometry geometry;
    private final List<Region> regions;
    private final List<Border> borders;
    private final RegionGraph graph;
    private final City city;
    private final Spawn spawn;
    private final List<Settlement> settlements;
    private final String fingerprint;
    private final Map<Long, WorldPlanRules.Tile> tileIndex;

    public WorldPlan(long seed,WorldPlanRules rules) {
        this.seed=seed;this.rules=rules;
        geometry=new RegionGeometry(seed,rules.coreRadius,rules.warpScale,rules.warpAmplitude);
        List<Region> positioned=positionRegions();List<GeoEdge> adjacency=adjacency(positioned);
        Set<String> backbone=spanningTree(positioned,adjacency);
        regions=assignTiers(positioned,adjacency,backbone);borders=typeBorders(adjacency,backbone);
        List<GenRegion> rs=new ArrayList<>();List<GenEdge> es=new ArrayList<>();
        for(Region r:regions){var p=r.profile;rs.add(new GenRegion(p.id(),p.name(),r.tier,p.role(),new IntRange(p.qiMin(),p.qiMax()),
            new IntRange(p.dangerMin(),p.dangerMax()),p.role().equals("walled")?List.of():List.of("sect"),p.nominalTier(),r.x/RegionPlacement.SCALE,r.z/RegionPlacement.SCALE));}
        for(Border e:borders)es.add(new GenEdge(e.a,e.b,e.passable?RegionContract.EDGE_LIAN:RegionContract.EDGE_GE,
            e.passable?null:(e.separator.equals("sea")?RegionContract.SEP_OCEAN:RegionContract.SEP_MOUNTAIN),e.gate?RegionContract.PASS_GUANAI:null));
        RegionGraph preliminary=new RegionGraph(seed,rules.algorithm,rs.size(),new IntRange(rules.tierMin,rules.tierMax),rules.stepMax,List.copyOf(rs),List.copyOf(es),geometry);
        city=chooseCity(preliminary);
        Map<Long, WorldPlanRules.Tile> index = new HashMap<>();
        for (var tile : rules.city.tiles()) index.put(((long)tile.x()<<32) ^ (tile.z()&0xffffffffL), tile);
        tileIndex = Map.copyOf(index);
        List<RegionGeometry.Reservation> reservations=new ArrayList<>();
        reservations.add(new RegionGeometry.Reservation(rules.city.id(),city.x,city.z,city.x+city.width-1,city.z+city.depth-1));
        spawn=chooseSpawn(preliminary);
        settlements=chooseSettlements();
        if(rules.settlements!=null)for(var site:settlements){
            int margin=rules.settlements.margin+rules.settlements.structureBuffer,n=rules.settlements.size;
            reservations.add(new RegionGeometry.Reservation(site.id,site.x-margin,site.z-margin,site.x+n+margin-1,site.z+n+margin-1));
        }
        RegionGeometry reserved=new RegionGeometry(seed,rules.coreRadius,rules.warpScale,rules.warpAmplitude,reservations);
        graph=new RegionGraph(seed,rules.algorithm,rs.size(),new IntRange(rules.tierMin,rules.tierMax),rules.stepMax,List.copyOf(rs),List.copyOf(es),reserved);
        fingerprint=WorldPlanRules.sha256(manifest(false).toString());
    }
    public long seed(){return seed;}
    public WorldPlanRules rules(){return rules;}
    public RegionGraph graph(){return graph;}
    public RegionGeometry geometry(){return geometry;}
    public List<Region> regions(){return regions;}
    public List<Border> borders(){return borders;}
    public City city(){return city;}
    public Spawn spawn(){return spawn;}
    public List<Settlement> settlements(){return settlements;}
    public String fingerprint(){return fingerprint;}
    private double random(long stream,long n){return unit(hash(seed,n,0,stream));}
    private int range(int lo,int hi,long stream,long n){return lo+(int)(random(stream,n)*(hi-lo+1));}
    private static String edgeKey(String a,String b){return a.compareTo(b)<0?a+":"+b:b+":"+a;}
    private String key(List<Region> rs,GeoEdge e){return edgeKey(rs.get(e.a).id(),rs.get(e.b).id());}

    private List<Region> positionRegions() {
        var anchor=rules.profiles.stream().filter(p->p.role().equals("anchor")).findFirst().orElseThrow();
        var walled=rules.profiles.stream().filter(p->p.role().equals("walled")).findFirst().orElseThrow();
        List<WorldPlanRules.Profile> peripheral=new ArrayList<>(rules.profiles.stream().filter(p->p.role().equals("peripheral")).toList());
        peripheral.sort(Comparator.<WorldPlanRules.Profile>comparingDouble(p->random(11,p.id().hashCode())).thenComparing(WorldPlanRules.Profile::id));
        int count=range(rules.minCount,rules.maxCount,12,0);peripheral=new ArrayList<>(peripheral.subList(0,count-2));
        List<Region> out=new ArrayList<>();
        out.add(new Region(anchor,(int)Math.round((random(13,0)*2-1)*rules.anchorJitter),(int)Math.round((random(13,1)*2-1)*rules.anchorJitter),rules.tierMax));
        double rotation=random(14,0)*2*StrictMath.PI;
        for(int i=0;i<2;i++){
            double angle=rotation+i*StrictMath.PI+(random(15,i)*2-1)*rules.angularJitter;
            double r=rules.innerMin+random(16,i)*(rules.innerMax-rules.innerMin);
            out.add(new Region(peripheral.get(i),(int)Math.round(StrictMath.cos(angle)*r),(int)Math.round(StrictMath.sin(angle)*r),0));
        }
        List<WorldPlanRules.Profile> outer=new ArrayList<>(peripheral.subList(2,peripheral.size()));outer.add(walled);
        outer.sort(Comparator.<WorldPlanRules.Profile>comparingDouble(p->random(17,p.id().hashCode())).thenComparing(WorldPlanRules.Profile::id));
        for(int i=0;i<outer.size();i++){
            double angle=rotation+StrictMath.PI/(outer.size()+1)+i*2*StrictMath.PI/outer.size()+(random(18,i)*2-1)*rules.angularJitter;
            double r=rules.outerMin+random(19,i)*(rules.outerMax-rules.outerMin);
            out.add(new Region(outer.get(i),(int)Math.round(StrictMath.cos(angle)*r),(int)Math.round(StrictMath.sin(angle)*r),0));
        }
        out.sort(Comparator.comparing(Region::id));return List.copyOf(out);
    }
    /** Half-plane clipped cells: graph neighbours physically share a boundary in the core. */
    private List<GeoEdge> adjacency(List<Region> rs) {
        List<GeoEdge> out=new ArrayList<>();
        for(int i=0;i<rs.size();i++){
            List<Point> polygon=new ArrayList<>();
            for(int a=0;a<64;a++){double theta=a*2*StrictMath.PI/64;polygon.add(new Point((rules.coreRadius-rules.warpAmplitude)*StrictMath.cos(theta),(rules.coreRadius-rules.warpAmplitude)*StrictMath.sin(theta)));}
            Region r=rs.get(i);
            for(int j=0;j<rs.size();j++)if(i!=j){Region q=rs.get(j);polygon=clip(polygon,q.x-r.x,q.z-r.z,((double)q.x*q.x+(double)q.z*q.z-(double)r.x*r.x-(double)r.z*r.z)/2);}
            if(polygon.isEmpty())throw new IllegalArgumentException("Empty core region "+r.id());
            for(int j=i+1;j<rs.size();j++){
                Region q=rs.get(j);double nx=q.x-r.x,nz=q.z-r.z,c=((double)q.x*q.x+(double)q.z*q.z-(double)r.x*r.x-(double)r.z*r.z)/2;
                List<Point> on=polygon.stream().filter(p->Math.abs(nx*p.x+nz*p.z-c)<0.01).toList();
                if(on.size()>=2&&distance(on.getFirst(),on.getLast())>1)out.add(new GeoEdge(i,j,on.getFirst(),on.getLast()));
            }
        }
        out.sort(Comparator.comparing(e->key(rs,e)));return out;
    }
    private static List<Point> clip(List<Point> polygon,double nx,double nz,double c) {
        if(polygon.isEmpty())return polygon;
        List<Point> out=new ArrayList<>();Point prev=polygon.getLast();double vp=nx*prev.x+nz*prev.z-c;
        for(Point next:polygon){double vn=nx*next.x+nz*next.z-c;
            if((vp<=0)!=(vn<=0)){double t=vp/(vp-vn);out.add(new Point(prev.x+t*(next.x-prev.x),prev.z+t*(next.z-prev.z)));}
            if(vn<=0)out.add(next);prev=next;vp=vn;
        }
        return out;
    }
    private Set<String> spanningTree(List<Region> rs,List<GeoEdge> edges) {
        List<GeoEdge> sorted=new ArrayList<>(edges.stream().filter(e->!walled(rs.get(e.a))&&!walled(rs.get(e.b))).toList());
        sorted.sort(Comparator.<GeoEdge>comparingDouble(e->StrictMath.hypot(rs.get(e.a).x-rs.get(e.b).x,rs.get(e.a).z-rs.get(e.b).z)*(0.75+random(20,key(rs,e).hashCode())/2)).thenComparing(e->key(rs,e)));
        int[] parent=new int[rs.size()];for(int i=0;i<parent.length;i++)parent[i]=i;
        Set<String> tree=new TreeSet<>();
        for(GeoEdge e:sorted){int a=e.a,b=e.b;while(parent[a]!=a)a=parent[a];while(parent[b]!=b)b=parent[b];if(a!=b){parent[b]=a;tree.add(key(rs,e));}}
        if(tree.size()!=rs.size()-2)throw new IllegalArgumentException("Core geometry cannot connect the non-walled regions");return tree;
    }
    private static boolean walled(Region r){return r.profile.role().equals("walled");}
    private List<Region> assignTiers(List<Region> rs,List<GeoEdge> edges,Set<String> tree) {
        int[] tier=new int[rs.size()];Arrays.fill(tier,-1);ArrayDeque<Integer> todo=new ArrayDeque<>();
        for(int i=0;i<rs.size();i++)if(rs.get(i).profile.role().equals("anchor")){tier[i]=rules.tierMax;todo.add(i);}
        while(!todo.isEmpty()){int i=todo.removeFirst();for(GeoEdge e:edges)if(tree.contains(key(rs,e))&&(e.a==i||e.b==i)){
            int j=e.a==i?e.b:e.a;if(tier[j]<0){tier[j]=Math.max(rules.tierMin,tier[i]-range(rules.stepMin,rules.stepMax,21,rs.get(j).id().hashCode()));todo.add(j);}
        }}
        for(int i=0;i<rs.size();i++)if(walled(rs.get(i))){final int wi=i;
            GeoEdge closest=edges.stream().filter(e->e.a==wi||e.b==wi).min(Comparator.comparingDouble(e->StrictMath.hypot(rs.get(e.a).x-rs.get(e.b).x,rs.get(e.a).z-rs.get(e.b).z))).orElseThrow();
            int j=closest.a==i?closest.b:closest.a;tier[i]=Math.max(rules.tierMin,tier[j]-range(0,rules.stepMax,22,rs.get(i).id().hashCode()));
        }
        List<Region> out=new ArrayList<>();for(int i=0;i<rs.size();i++)out.add(new Region(rs.get(i).profile,rs.get(i).x,rs.get(i).z,tier[i]));return List.copyOf(out);
    }
    private List<Border> typeBorders(List<GeoEdge> edges,Set<String> tree) {
        GeoEdge gate=null;
        if(random(23,0)<rules.walledPassChance)gate=edges.stream().filter(e->walled(regions.get(e.a))||walled(regions.get(e.b)))
            .min(Comparator.comparingDouble(e->StrictMath.hypot(regions.get(e.a).x-regions.get(e.b).x,regions.get(e.a).z-regions.get(e.b).z))).orElse(null);
        List<Border> out=new ArrayList<>();
        for(GeoEdge e:edges){Region a=regions.get(e.a),b=regions.get(e.b);String k=key(regions,e);boolean w=walled(a)||walled(b);
            boolean pass=e.equals(gate)||(!w&&(tree.contains(k)||(Math.abs(a.tier-b.tier)<=rules.stepMax&&random(24,k.hashCode())<rules.extraRouteChance)));
            String separator=w?"ridge":(!pass&&random(25,k.hashCode())<rules.seaSeparatorChance?"sea":"ridge");
            out.add(new Border(a.id(),b.id(),e.p,e.q,pass,separator,e.equals(gate)));
        }
        return List.copyOf(out);
    }
    private City chooseCity(RegionGraph graph) {
        Region host=regions.stream().filter(r->r.id().equals(rules.city.host())).findFirst().orElseThrow();double[] actual=geometry.unwarp(host.x,host.z);
        for(int attempt=0;attempt<=rules.city.placementAttempts();attempt++){
            double radius=attempt==rules.city.placementAttempts()?0:StrictMath.sqrt(random(30,attempt))*rules.city.placementRadius();double angle=random(31,attempt)*2*StrictMath.PI;
            int x=Math.floorDiv((int)Math.round(actual[0]+radius*StrictMath.cos(angle))-rules.city.width()/2,16)*16;
            int z=Math.floorDiv((int)Math.round(actual[1]+radius*StrictMath.sin(angle))-rules.city.depth()/2,16)*16;
            City candidate=new City(x,z,rules.city.baseY(),rules.city.width(),rules.city.height(),rules.city.depth(),host.id());
            boolean valid=true;int margin=rules.city.skirt()+rules.city.structureBuffer();
            for(int dx:new int[]{-margin,rules.city.width()/2,rules.city.width()+margin})for(int dz:new int[]{-margin,rules.city.depth()/2,rules.city.depth()+margin})valid&=RegionQueries.regionAt(graph,x+dx,z+dz).orElse("").equals(host.id());
            if(valid)return candidate;
        }
        throw new IllegalArgumentException("No safe city reservation in anchor; invalid rules/geometry");
    }
    private Spawn chooseSpawn(RegionGraph queryGraph) {
        Region region=regions.stream().filter(r->!walled(r)).min(Comparator.comparingInt(Region::tier).thenComparing(Region::id)).orElseThrow();
        double[] center=geometry.unwarp(region.x,region.z);
        for(int i=0;i<rules.spawnAttempts;i++){
            double angle=random(50,i)*2*StrictMath.PI,radius=StrictMath.sqrt(random(51,i))*rules.spawnSearchRadius;
            int x=(int)Math.round(center[0]+StrictMath.cos(angle)*radius),z=(int)Math.round(center[1]+StrictMath.sin(angle)*radius);
            if(!RegionQueries.regionAt(queryGraph,x,z).orElse("").equals(region.id()))continue;
            double h=baseValues(x,z).height;if(h<rules.spawnMinimumY||h>rules.maxHeight-16)continue;
            if(Math.abs(baseValues(x+8,z).height-h)>4||Math.abs(baseValues(x,z+8).height-h)>4)continue;
            return new Spawn(x,(int)StrictMath.floor(h),z,region.id(),false);
        }
        int x=(int)Math.round(center[0]),z=(int)Math.round(center[1]);
        return new Spawn(x,Math.max(rules.spawnMinimumY,(int)StrictMath.floor(baseValues(x,z).height)),z,region.id(),true);
    }
    public Optional<WorldPlanRules.Tile> cityTile(int chunkX,int chunkZ) {
        long x=(long)chunkX*16-city.x,z=(long)chunkZ*16-city.z;
        if(x<0||z<0||x>=city.width||z>=city.depth)return Optional.empty();
        return Optional.ofNullable(tileIndex.get((x<<32) ^ (z&0xffffffffL)));
    }
    public boolean reserved(int x,int y,int z) {
        if(rules.settlements!=null)for(var site:settlements){
            var r=rules.settlements;
            if(x>=site.x+r.inset&&x<site.x+r.size-r.inset&&z>=site.z+r.inset&&z<site.z+r.size-r.inset&&y>=site.offsetY+r.baseY)return true;
        }
        return(city.contains(x,z,rules.city.margin())&&y>=city.baseY-rules.surfaceSeal&&y<city.baseY+city.height)
            ||(Math.abs((long)x-spawn.x)<=rules.spawnPadRadius&&Math.abs((long)z-spawn.z)<=rules.spawnPadRadius&&y>=spawn.y-1
                &&(rules.clearSpawnCanopy()||y<spawn.y+5));
    }
    public boolean intersectsCity(int minX,int minZ,int maxX,int maxZ) {
        int m=rules.city.structureBuffer()+rules.city.margin();return minX<city.x+city.width+m&&maxX>=city.x-m&&minZ<city.z+city.depth+m&&maxZ>=city.z-m;
    }
    public boolean intersectsSettlements(int minX,int minZ,int maxX,int maxZ){
        if(rules.settlements==null)return false;
        var r=rules.settlements;
        for(var site:settlements)if(intersects(site,r.margin+r.structureBuffer,r.size,minX,minZ,maxX,maxZ))return true;
        return false;
    }
    public Optional<Settlement> settlementChunk(int chunkX,int chunkZ){
        if(rules.settlements==null)return Optional.empty();
        var r=rules.settlements;
        return settlements.stream().filter(site->intersects(site,r.margin,r.size,chunkX*16,chunkZ*16,chunkX*16+15,chunkZ*16+15)).findFirst();
    }
    private static boolean intersects(Settlement site,int margin,int size,int minX,int minZ,int maxX,int maxZ){
        return minX<site.x+size+margin&&maxX>=site.x-margin&&minZ<site.z+size+margin&&maxZ>=site.z-margin;
    }
    private List<Settlement> chooseSettlements(){
        var r=rules.settlements;if(r==null)return List.of();
        List<Settlement> selected=new ArrayList<>();int extent=r.size/2+r.margin,clearance=r.structureBuffer;
        for(var region:regions){
            if(selected.size()>=r.maximumSites)break;
            if(!r.allowWalled&&walled(region))continue;
            double[] center=geometry.unwarp(region.x,region.z);
            for(int attempt=0;attempt<r.attempts;attempt++){
                long candidateSeed=hash(seed,region.id().hashCode(),attempt,r.salt);
                double angle=unit(mix(candidateSeed))*2*StrictMath.PI;
                double radius=StrictMath.sqrt(unit(mix(candidateSeed^0x53495445L)))*r.searchRadius;
                int cx=Math.floorDiv((int)Math.round(center[0]+radius*StrictMath.cos(angle)),16)*16;
                int cz=Math.floorDiv((int)Math.round(center[1]+radius*StrictMath.sin(angle)),16)*16;
                if(intersectsCity(cx-extent-clearance,cz-extent-clearance,cx+extent+clearance-1,cz+extent+clearance-1))continue;
                int spawnMargin=extent+clearance+rules.spawnPadRadius+rules.spawnSkirt;
                if(Math.abs(cx-spawn.x)<=spawnMargin&&Math.abs(cz-spawn.z)<=spawnMargin)continue;
                boolean valid=true;
                for(var previous:selected)if(intersects(previous,r.margin+clearance,r.size,cx-extent,cz-extent,cx+extent-1,cz+extent-1)){valid=false;break;}
                if(!valid)continue;
                // Complete footprint stays in one eligible region, including its transition skirt.
                for(int dx:new int[]{-extent,0,extent-1})for(int dz:new int[]{-extent,0,extent-1}){
                    var context=sample(cx+dx,cz+dz);
                    if(!context.core()||!context.regionId().equals(region.id()))valid=false;
                }
                if(!valid||!r.biomes.contains(sample(cx,cz).biome()))continue;
                int[] offsets=r.sampleOffsets(),heights=new int[offsets.length*offsets.length];int i=0;
                for(int dx:offsets)for(int dz:offsets){
                    int h=surfaceY(cx+dx,cz+dz);heights[i++]=h;
                    if(h<rules.seaLevel+r.minAboveSea||h>r.maxSurface)valid=false;
                }
                Arrays.sort(heights);
                if(!valid||heights[heights.length-1]-heights[0]>r.maxRelief)continue;
                int offsetY=heights[heights.length/2]-r.anchorLevel;
                if(offsetY+r.baseY< -64||offsetY+r.baseY+r.height>=320)continue;
                selected.add(new Settlement("bay_"+region.id(),r.layout,r.layoutHash,region.id(),cx-r.size/2,offsetY,cz-r.size/2,(int)(candidateSeed&3),candidateSeed));
                break;
            }
        }
        return List.copyOf(selected);
    }
    public int surfaceY(int x,int z){return(int)StrictMath.floor(height(x,z));}
    public double height(double x,double z){return values(x,z).height;}
    private Region nearest(double x,double z) {
        Region best=null;double d=Double.POSITIVE_INFINITY;for(Region r:regions){double q=StrictMath.hypot(x-r.x,z-r.z);if(q<d){d=q;best=r;}}return best;
    }
    private Values coreValues(double x,double z) {
        double[] q=geometry.warp(x,z);Region nearest=nearest(q[0],q[1]);
        double min=StrictMath.hypot(q[0]-nearest.x,q[1]-nearest.z),sum=0,h=0,qi=0,danger=0;
        for(Region r:regions){double w=1-(StrictMath.hypot(q[0]-r.x,q[1]-r.z)-min)/rules.terrainBlend;
            if(w<=0)continue;w=smooth(w);sum+=w;h+=w*profileHeight(r.profile,x,z);qi+=w*r.profile.qi();danger+=w*r.profile.danger();}
        h/=sum;qi/=sum;danger/=sum;
        for(Border e:borders)if(!e.passable||e.gate){
            double influence=1-smooth(segmentDistance(q[0],q[1],e.p,e.q)/rules.boundaryWidth);
            if(e.gate)influence*=smooth(StrictMath.hypot(q[0]-e.crossing().x,q[1]-e.crossing().z)/rules.passWidth);
            if(e.separator.equals("sea"))h=lerp(h,rules.seaLevel-rules.channelDepth,influence);else h+=rules.ridgeLift*influence;
        }
        return new Values(h,qi,danger);
    }
    private double profileHeight(WorldPlanRules.Profile p,double x,double z) {
        long s=seed^mix(p.id().hashCode());double broad=noise(s,x/rules.terrainScale,z/rules.terrainScale);
        double medium=noise(s^0x523ACF21L,x/(rules.terrainScale/2),z/(rules.terrainScale/2));
        double detail=noise(seed^0x13BE7489L,x/rules.detailScale,z/rules.detailScale);
        return p.base()+p.amplitude()*(broad*0.75+medium*0.25)+detail*rules.detailAmplitude;
    }
    private Outer outer(long gx,long gz) {
        long h=hash(seed,gx,gz,40);double x=(gx+0.5+(unit(h)*2-1)*rules.outerJitter)*rules.outerCellSize;
        double z=(gz+0.5+(unit(mix(h))*2-1)*rules.outerJitter)*rules.outerCellSize;
        int total=rules.profiles.stream().mapToInt(WorldPlanRules.Profile::outerWeight).sum();int pick=(int)(unit(mix(h^41))*total);
        WorldPlanRules.Profile profile=rules.profiles.getFirst();for(var p:rules.profiles){pick-=p.outerWeight();if(pick<0){profile=p;break;}}
        int tier=Math.max(rules.tierMin,Math.min(rules.tierMax,profile.nominalTier()+range(-rules.stepMax,rules.stepMax,42,h)));
        return new Outer(gx,gz,x,z,profile,tier);
    }
    private List<Outer> neighbourhood(double x,double z) {
        long gx=(long)StrictMath.floor(x/rules.outerCellSize),gz=(long)StrictMath.floor(z/rules.outerCellSize);List<Outer> cells=new ArrayList<>(25);
        // Jitter <= .2 and blend <= cellSize/3 bound every contributing cell to this halo.
        for(long dx=-2;dx<=2;dx++)for(long dz=-2;dz<=2;dz++)cells.add(outer(gx+dx,gz+dz));return cells;
    }
    private Outer nearestOuter(double x,double z,List<Outer> cells) {
        Outer best=null;double min=Double.POSITIVE_INFINITY;for(Outer o:cells){double d=StrictMath.hypot(x-o.x,z-o.z);if(d<min){min=d;best=o;}}return best;
    }
    private Values outerValues(double x,double z) {
        List<Outer> cells=neighbourhood(x,z);Outer nearest=nearestOuter(x,z,cells);double min=StrictMath.hypot(x-nearest.x,z-nearest.z),sum=0,h=0,qi=0,danger=0;
        for(Outer o:cells){double w=1-(StrictMath.hypot(x-o.x,z-o.z)-min)/rules.terrainBlend;
            if(w<=0)continue;w=smooth(w);sum+=w;h+=w*profileHeight(o.profile,x,z);qi+=w*o.profile.qi();danger+=w*o.profile.danger();}
        return new Values(h/sum,qi/sum,danger/sum);
    }
    private Values baseValues(double x,double z) {
        double radius=StrictMath.hypot(x,z),blend=smooth((radius-(rules.coreRadius-rules.coreBlend))/(2*rules.coreBlend));
        Values a=blend>=1?null:coreValues(x,z),b=blend<=0?null:outerValues(x,z);double h,qi,danger;
        if(a==null){h=b.height;qi=b.qi;danger=b.danger;}else if(b==null){h=a.height;qi=a.qi;danger=a.danger;}
        else{h=lerp(a.height,b.height,blend);qi=lerp(a.qi,b.qi,blend);danger=lerp(a.danger,b.danger,blend);}
        double dx=Math.max(Math.max(city.x-x,x-(city.x+city.width-1)),0),dz=Math.max(Math.max(city.z-z,z-(city.z+city.depth-1)),0);
        double pad=1-smooth(StrictMath.hypot(dx,dz)/rules.city.skirt());
        if(pad>0)h=lerp(h,city.baseY+rules.city.surface((int)StrictMath.floor(x-city.x),(int)StrictMath.floor(z-city.z)),pad);
        h=Math.max(rules.minHeight,Math.min(rules.maxHeight,h));
        qi=Math.max(0,qi+noise(seed^0x71954A1L,x/(rules.terrainScale*2),z/(rules.terrainScale*2))*rules.qiVariation);
        return new Values(h,qi,danger);
    }
    private Values values(double x,double z) {
        Values v=baseValues(x,z);double d=Math.max(Math.abs(x-spawn.x),Math.abs(z-spawn.z));
        double influence=1-smooth((d-rules.spawnPadRadius)/rules.spawnSkirt);return new Values(lerp(v.height,spawn.y,influence),v.qi,v.danger);
    }
    public boolean allowsStructure(String id,int x,int z) {
        var policy=rules.structurePolicies.get(id);if(policy==null)return true;Context c=sample(x,z);
        return(policy.allowOuter()||c.core())&&(policy.allowWalled()||!rules.profile(c.profileId()).role().equals("walled"))
            &&c.qi()>=policy.minimumQi()&&c.tier()>=policy.minimumTier()&&c.tier()<=policy.maximumTier();
    }
    public Context sample(int x,int z) {
        Values v=values(x,z);WorldPlanRules.Profile p;String id,name;int tier;boolean core=StrictMath.hypot(x,z)<=rules.coreRadius;
        if(core){double[] q=geometry.warp(x,z);Region r=nearest(q[0],q[1]);p=r.profile;id=p.id();name=p.name();tier=r.tier;}
        else{Outer o=nearestOuter(x,z,neighbourhood(x,z));p=o.profile;id="outer/"+o.gx+"/"+o.gz;name=p.name()+"外域〔"+o.gx+","+o.gz+"〕";tier=o.tier;}
        int surface=(int)StrictMath.floor(v.height);String biome;
        if(surface<rules.seaLevel-8)biome="minecraft:deep_ocean";
        else if(surface<rules.seaLevel)biome="minecraft:ocean";
        else if(surface<=rules.seaLevel+2)biome="minecraft:beach";
        else if(rules.ecotone!=null)biome=rules.ecotone.apply(x,z,originalLandBiome(p,x,z),this::originalBiomeAt);
        else if(rules.ecology!=null)biome=rules.ecology.biome(climate(x,z,v.height));
        else biome=originalLandBiome(p,x,z);
        return new Context(id,name,p.id(),core,tier,v.qi,v.danger,surface,biome);
    }
    private String originalLandBiome(WorldPlanRules.Profile p,int x,int z){
        double n=noise(seed^mix(p.id().hashCode())^43,x/(rules.terrainScale*2),z/(rules.terrainScale*2));
        return p.biomes().get(n<-.22?0:n>.22?2:1);
    }
    /** Unmodified V1 source, never the output of another transition. */
    public String originalBiomeAt(int x,int z){
        int surface=surfaceY(x,z);
        if(surface<rules.seaLevel-8)return "minecraft:deep_ocean";
        if(surface<rules.seaLevel)return "minecraft:ocean";
        if(surface<=rules.seaLevel+2)return "minecraft:beach";
        WorldPlanRules.Profile p;
        if(StrictMath.hypot(x,z)<=rules.coreRadius){double[] q=geometry.warp(x,z);p=nearest(q[0],q[1]).profile;}
        else p=nearestOuter(x,z,neighbourhood(x,z)).profile;
        return originalLandBiome(p,x,z);
    }
    public WorldPlanEcology.Climate climate(double x,double z) { return climate(x,z,height(x,z)); }
    private WorldPlanEcology.Climate climate(double x,double z,double height) {
        WorldPlanEcology e=Objects.requireNonNull(rules.ecology,"V1 does not use blended climate");
        double transition=smooth((StrictMath.hypot(x,z)-(rules.coreRadius-rules.coreBlend))/(2*rules.coreBlend));
        WorldPlanEcology.Climate c;
        if(transition<=0)c=coreClimate(x,z);
        else if(transition>=1)c=outerClimate(x,z);
        else c=coreClimate(x,z).mix(outerClimate(x,z),transition);
        double temperature=c.temperature()+noise(seed^0x45434F54454D50L,x/e.noiseScale,z/e.noiseScale)*e.temperatureVariation
            -Math.max(0,height-e.coolingStartY)*e.coolingPerBlock;
        double moisture=c.moisture()+noise(seed^0x45434F4D4F4953L,x/e.noiseScale,z/e.noiseScale)*e.moistureVariation;
        return new WorldPlanEcology.Climate(Math.clamp(temperature,0,1),Math.clamp(moisture,0,1));
    }
    private WorldPlanEcology.Climate coreClimate(double x,double z) {
        double[] q=geometry.warp(x,z);Region closest=nearest(q[0],q[1]);
        double min=StrictMath.hypot(q[0]-closest.x,q[1]-closest.z),sum=0,t=0,m=0;
        for(Region r:regions){double w=1-(StrictMath.hypot(q[0]-r.x,q[1]-r.z)-min)/rules.ecology.blend;
            if(w<=0)continue;w=smooth(w);var c=rules.ecology.profile(r.id());sum+=w;t+=w*c.temperature();m+=w*c.moisture();}
        return new WorldPlanEcology.Climate(t/sum,m/sum);
    }
    private WorldPlanEcology.Climate outerClimate(double x,double z) {
        List<Outer> cells=neighbourhood(x,z);Outer closest=nearestOuter(x,z,cells);
        double min=StrictMath.hypot(x-closest.x,z-closest.z),sum=0,t=0,m=0;
        for(Outer r:cells){double w=1-(StrictMath.hypot(x-r.x,z-r.z)-min)/rules.ecology.blend;
            if(w<=0)continue;w=smooth(w);var c=rules.ecology.profile(r.profile.id());sum+=w;t+=w*c.temperature();m+=w*c.moisture();}
        return new WorldPlanEcology.Climate(t/sum,m/sum);
    }
    public JsonObject manifest(){return manifest(true);}
    private JsonObject manifest(boolean includeFingerprint) {
        JsonObject root=new JsonObject();root.addProperty("schema",1);root.addProperty("algorithm",rules.algorithm);
        root.addProperty("seed",seed);root.addProperty("rules_sha256",rules.fingerprint());
        if(includeFingerprint)root.addProperty("plan_sha256",fingerprint);
        root.add("rules",JsonParser.parseString(rules.json()));root.add("graph",JsonParser.parseString(graph.toCanonicalJson()));
        root.add("borders",WorldPlanRules.JSON.toJsonTree(borders));root.add("city",WorldPlanRules.JSON.toJsonTree(city));root.add("spawn",WorldPlanRules.JSON.toJsonTree(spawn));
        if(rules.settlements!=null)root.add("settlements",WorldPlanRules.JSON.toJsonTree(settlements));
        root.addProperty("height_semantics","first air above sealed terrain, before buildings/trees; sea surface=max(terrain,sea_level)");
        root.addProperty("outer_rule","bounded 5x5 lattice halo, stable cell IDs; no distance-based difficulty escalation or world pre-generation");return root;
    }
    public static double segmentDistance(double x,double z,Point a,Point b) {
        double dx=b.x-a.x,dz=b.z-a.z,den=dx*dx+dz*dz,t=den==0?0:Math.max(0,Math.min(1,((x-a.x)*dx+(z-a.z)*dz)/den));
        return StrictMath.hypot(x-a.x-t*dx,z-a.z-t*dz);
    }
    private static double distance(Point a,Point b){return StrictMath.hypot(a.x-b.x,a.z-b.z);}
    private static double lerp(double a,double b,double t){return a+(b-a)*t;}
}
