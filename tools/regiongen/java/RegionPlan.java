package com.example.myvillage.regiongen;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;

/** Pure bounded offline prototype. No Minecraft dependency or world reads. */
public final class RegionPlan {
    public static final String ALGORITHM = "myvillage:river_region_sample_v1";
    public record Point(int x, int y, int z) {}
    public record Cell(int x, int z, int height, String facing, boolean bridge) {}
    public record Route(String id, List<Point> points, int width) {}
    public record Building(String id, String cluster, String asset, int x, int y, int z,
                           int rotation, int width, int depth, int height, Point approach,
                           int[] pad) {}
    public record Cluster(String id, String name, int x, int z, Point north, Point south) {}
    private final JsonObject recipe, catalog;
    private final long seed;
    private final int n, landing, spacing;
    private final int[] natural, heights, kinds, forced;
    private final List<Building> buildings = new ArrayList<>();
    private final List<Cluster> clusters = new ArrayList<>();
    private final List<Route> routes = new ArrayList<>();
    private final LinkedHashMap<Integer, Cell> roads = new LinkedHashMap<>();
    private final List<double[]> hills = new ArrayList<>();
    private final double phase;
    private Point spawn;

    public RegionPlan(long seed, JsonObject recipe, JsonObject catalog) {
        this.seed = seed; this.recipe = recipe.deepCopy(); this.catalog = catalog.deepCopy();
        n = integer(recipe, "size");
        if (n < 128 || n > 512 || integer(recipe,"schema") != 1) throw new IllegalArgumentException("Unsupported bounded recipe");
        var layout = recipe.getAsJsonObject("layout");
        landing = integer(layout,"landing"); spacing = integer(layout,"step_spacing");
        if (landing < 2 || spacing < 2) throw new IllegalArgumentException("Invalid road bounds");
        natural = new int[n*n]; heights = new int[n*n]; kinds = new int[n*n]; forced = new int[n*n];
        Arrays.fill(forced, Integer.MIN_VALUE);
        phase = random("river",0)*Math.PI*2;
        JsonObject terrain = recipe.getAsJsonObject("terrain");
        int i = 0;
        for (var item : terrain.getAsJsonArray("hills")) {
            var a = item.getAsJsonArray();
            double jitter = number(terrain,"hill_jitter")*n;
            hills.add(new double[]{a.get(0).getAsDouble()*n+(random("hill_x",i)-.5)*jitter,
                a.get(1).getAsDouble()*n+(random("hill_z",i)-.5)*jitter,
                a.get(2).getAsDouble()*(1+(random("hill_h",i)-.5)*number(terrain,"amplitude_jitter")),
                a.get(3).getAsDouble()*n,a.get(4).getAsDouble()*n}); i++;
        }
        for(int z=0;z<n;z++) for(int x=0;x<n;x++) natural[z*n+x] = (int)Math.round(base(x,z));
        System.arraycopy(natural,0,heights,0,n*n);
        planClusters(); planConnections(); blendTerrain(); validate();
    }
    private static int integer(JsonObject j,String key){return j.get(key).getAsInt();}
    private static double number(JsonObject j,String key){return j.get(key).getAsDouble();}
    private static String string(JsonObject j,String key){return j.get(key).getAsString();}
    private double random(String domain,int index){
        long h = seed ^ ((long)domain.hashCode()<<32) ^ index;
        h = (h^(h>>>30))*0xbf58476d1ce4e5b9L; h=(h^(h>>>27))*0x94d049bb133111ebL; h^=h>>>31;
        return (h>>>11)*0x1.0p-53;
    }
    private static double smooth(double t){t=Math.max(0,Math.min(1,t));return t*t*(3-2*t);}
    private double riverX(double z){var r=recipe.getAsJsonObject("river");return n*number(r,"center")+n*number(r,"bend")*Math.sin(z/(n*number(r,"period"))*2*Math.PI+phase);}
    private double base(double x,double z){
        var t=recipe.getAsJsonObject("terrain");var r=recipe.getAsJsonObject("river");
        double h=number(t,"base");
        for(var hill:hills) h+=hill[2]*Math.exp(-Math.pow((x-hill[0])/hill[3],2)-Math.pow((z-hill[1])/hill[4],2));
        double s=number(t,"detail_scale");
        h+=number(t,"detail_amplitude")*(Math.sin(x/s+phase)*Math.cos(z/(s*1.3)-phase));
        double d=Math.abs(x-riverX(z)),width=number(r,"half_width"),bank=number(r,"bank_width");
        double bed=number(r,"water_y")-number(r,"bed_depth");
        if(d<width) return bed;
        if(d<width+bank){double a=smooth((d-width)/bank);return bed*(1-a)+h*a;}
        return h;
    }
    private int sample(int x,int z){if(x<0||z<0||x>=n||z>=n)throw new IllegalArgumentException("Point outside region");return natural[z*n+x];}
    private int[] rotate(int x,int z,int w,int d,int q){for(int i=0;i<q;i++){int tx=d-1-z;z=x;x=tx;int tw=w;w=d;d=tw;}return new int[]{x,z};}
    private void force(int x,int z,int y,int kind){
        if(x<1||z<1||x>=n-1||z>=n-1)throw new IllegalArgumentException("Write touches region edge");
        int i=z*n+x;
        if(forced[i]!=Integer.MIN_VALUE && forced[i]!=y)throw new IllegalArgumentException("Conflicting heights at "+x+","+z+": "+forced[i]+" vs "+y);
        forced[i]=y; kinds[i]=Math.max(kinds[i],kind);
    }
    private void planClusters(){
        var layout=recipe.getAsJsonObject("layout"); int jitter=integer(layout,"position_jitter"),gap=integer(layout,"front_gap");
        for(var elem:recipe.getAsJsonArray("clusters")){
            var c=elem.getAsJsonObject();String id=string(c,"id");var center=c.getAsJsonArray("center");
            int x=(int)Math.round(center.get(0).getAsDouble()*n+(random(id,0)-.5)*jitter*2);
            int z=(int)Math.round(center.get(1).getAsDouble()*n+(random(id,1)-.5)*jitter*2);
            var rows=c.getAsJsonArray("rows"); List<Point> stops=new ArrayList<>();
            for(var row:rows){int rz=z+row.getAsInt();stops.add(new Point(x,sample(x,rz),rz));}
            int frontage=0;
            for(var rowAssets:c.getAsJsonArray("assets"))for(var assetId:rowAssets.getAsJsonArray()){
                var asset=catalog.getAsJsonObject(assetId.getAsString());int ax=asset.getAsJsonArray("entry").get(0).getAsInt();
                frontage=Math.max(frontage,Math.max(ax,asset.getAsJsonArray("size").get(0).getAsInt()-1-ax));
            }
            int margin=frontage+integer(recipe.getAsJsonObject("terrain"),"foundation_margin")+integer(layout,"road_width")/2+landing;
            Point first=stops.getFirst(),last=stops.getLast();
            Point north=new Point(x,first.y,first.z-margin),south=new Point(x,last.y,last.z+margin);
            List<Point> street=new ArrayList<>();street.add(north);street.addAll(stops);street.add(south);
            route(id+"_street",street,integer(layout,"road_width"),false);
            clusters.add(new Cluster(id,string(c,"name"),x,z,north,south));
            for(int row=0;row<rows.size();row++)for(int side=0;side<2;side++){
                String asset=c.getAsJsonArray("assets").get(row).getAsJsonArray().get(side).getAsString();
                var a=catalog.getAsJsonObject(asset);var size=a.getAsJsonArray("size");var entry=a.getAsJsonArray("entry");
                int w=size.get(0).getAsInt(),d=size.get(2).getAsInt(),q=side==0?1:3;
                int[] door=rotate(entry.get(0).getAsInt(),-1,w,d,q);
                int bx=side==0?x-gap-d:x+gap;
                Point stop=stops.get(row);int bz=stop.z-door[1],by=stop.y-entry.get(1).getAsInt();
                int padMargin=integer(recipe.getAsJsonObject("terrain"),"foundation_margin");
                int[] pad={bx-padMargin,bz-padMargin,bx+d-1+padMargin,bz+w-1+padMargin};
                for(int pz=pad[1];pz<=pad[3];pz++)for(int px=pad[0];px<=pad[2];px++)force(px,pz,stop.y,1);
                Point approach=new Point(bx+door[0],stop.y,bz+door[1]);
                route(id+"_door_"+row+"_"+side,List.of(stop,approach),integer(layout,"path_width"),false);
                buildings.add(new Building(id+"_"+row+"_"+side,id,asset,bx,by,bz,q,d,w,size.get(1).getAsInt(),approach,pad));
            }
        }
    }
    private Cluster cluster(String id){return clusters.stream().filter(c->c.id.equals(id)).findFirst().orElseThrow();}
    private Point end(Cluster c,String name){return switch(name){case "north"->c.north;case "south"->c.south;default->throw new IllegalArgumentException("Invalid cluster end");};}
    private void planConnections(){
        int width=integer(recipe.getAsJsonObject("layout"),"path_width");
        for(var elem:recipe.getAsJsonArray("connections")){
            var c=elem.getAsJsonObject();Point a=end(cluster(string(c,"from")),string(c,"from_end"));Point b=end(cluster(string(c,"to")),string(c,"to_end"));
            int mid=(int)Math.round(a.z+(b.z-a.z)*number(c,"bend_fraction"));
            List<Point> via=List.of(a,new Point(a.x,0,mid),new Point(b.x,0,mid),b);
            if(capacity(via)<Math.abs(b.y-a.y)){
                int west=Math.min(a.x,b.x)-integer(recipe.getAsJsonObject("layout"),"detour");
                via=List.of(a,new Point(west,0,a.z),new Point(west,0,b.z),b);
            }
            route(string(c,"id"),graded(via),width,false);
        }
        Cluster village=clusters.getFirst();var e=recipe.getAsJsonObject("entry");
        spawn=new Point(village.x,village.south.y,(int)(n*number(e,"south_z")));
        route("arrival",List.of(spawn,village.south),integer(recipe.getAsJsonObject("layout"),"road_width"),false);
        Point a=village.north;
        Point b=new Point((int)(n*number(e,"bridge_east_x")),a.y,a.z);
        route("river_bridge",List.of(a,b),integer(recipe.getAsJsonObject("layout"),"road_width"),true);
    }
    private int capacity(List<Point> points){int result=0;for(int i=1;i<points.size();i++)result+=segmentCapacity(points.get(i-1),points.get(i));return result;}
    private int segmentCapacity(Point a,Point b){return Math.max(0,(Math.abs(a.x-b.x)+Math.abs(a.z-b.z)-2*landing)/spacing);}
    private List<Point> graded(List<Point> points){
        int delta=points.getLast().y-points.getFirst().y,cap=capacity(points),used=0,y=points.getFirst().y;
        if(Math.abs(delta)>cap)throw new IllegalArgumentException("Connection has no feasible bounded grade");
        List<Point> out=new ArrayList<>();out.add(points.getFirst());
        for(int i=1;i<points.size();i++){
            used+=segmentCapacity(points.get(i-1),points.get(i));
            int rise=cap==0?0:(int)Math.round(Math.abs(delta)*used/(double)cap);
            Point p=points.get(i);out.add(new Point(p.x,y+Integer.signum(delta)*rise,p.z));
        }
        return out;
    }
    private void route(String id,List<Point> stops,int width,boolean crossing){
        if(width<1||width%2==0)throw new IllegalArgumentException("Road width must be odd");
        List<Point> points=new ArrayList<>();
        for(int s=1;s<stops.size();s++){
            Point a=stops.get(s-1),b=stops.get(s);int dx=Integer.signum(b.x-a.x),dz=Integer.signum(b.z-a.z);
            if(dx!=0&&dz!=0)throw new IllegalArgumentException("Route segment must be axis aligned");
            int len=Math.abs(b.x-a.x)+Math.abs(b.z-a.z),dy=b.y-a.y;
            if(len==0){if(dy!=0)throw new IllegalArgumentException("Vertical road");continue;}
            if(dy!=0&&Math.abs(dy)*spacing>len-2*landing)throw new IllegalArgumentException("Grade exceeds road budget: "+id+" length="+len+" rise="+dy);
            for(int k=0;k<=len;k++){
                if(s>1&&k==0)continue;
                double t=Math.max(0,Math.min(1,(k-landing)/(double)Math.max(1,len-2*landing)));
                int y=a.y+(int)Math.floor(Math.abs(dy)*t+.5)*Integer.signum(dy);
                Point p=new Point(a.x+dx*k,y,a.z+dz*k); points.add(p);
            }
        }
        if(points.size()<2)throw new IllegalArgumentException("Empty road");
        routes.add(new Route(id,List.copyOf(points),width));
        for(int i=0;i<points.size();i++){
            Point p=points.get(i);String facing="";
            if(i>0&&points.get(i-1).y<p.y){Point low=points.get(i-1);facing=direction(p.x-low.x,p.z-low.z);}
            if(i+1<points.size()&&points.get(i+1).y<p.y){Point low=points.get(i+1);facing=direction(p.x-low.x,p.z-low.z);}
            int half=width/2;
            // A cross-section for straight sections, square landings at corners/ends.
            Point before=points.get(Math.max(0,i-1)),after=points.get(Math.min(points.size()-1,i+1));
            boolean alongX=before.z==after.z,alongZ=before.x==after.x;
            for(int oz=-half;oz<=half;oz++)for(int ox=-half;ox<=half;ox++){
                if(alongX&&ox!=0||alongZ&&oz!=0)continue;
                int x=p.x+ox,z=p.z+oz,key=z*n+x;var river=recipe.getAsJsonObject("river");
                boolean bridge=crossing&&Math.abs(x-riverX(z))<number(river,"half_width")+number(river,"bank_width")&&sample(x,z)<p.y-1;
                if(!bridge)force(x,z,p.y,2);
                else if(p.y-number(river,"water_y")<number(recipe.getAsJsonObject("entry"),"bridge_clearance"))throw new IllegalArgumentException("Bridge lacks clearance");
                Cell old=roads.get(key);Cell cell=new Cell(x,z,p.y,facing,bridge);
                if(old!=null&&(old.height!=p.y||!old.facing.isEmpty()&&!facing.isEmpty()&&!old.facing.equals(facing)))throw new IllegalArgumentException("Road junction conflict "+id);
                if(old==null||!facing.isEmpty())roads.put(key,cell);
            }
        }
    }
    private static String direction(int x,int z){if(x>0)return "east";if(x<0)return "west";if(z>0)return "south";return "north";}
    private void blendTerrain(){
        int radius=integer(recipe.getAsJsonObject("terrain"),"blend_radius");
        double[] weight=new double[n*n],delta=new double[n*n];
        for(int z=0;z<n;z++)for(int x=0;x<n;x++){
            int i=z*n+x;if(forced[i]==Integer.MIN_VALUE)continue;
            for(int dz=-radius;dz<=radius;dz++)for(int dx=-radius;dx<=radius;dx++){
                int xx=x+dx,zz=z+dz;if(xx<0||zz<0||xx>=n||zz>=n)continue;
                double w=1-smooth(Math.hypot(dx,dz)/radius);int j=zz*n+xx;
                if(w>weight[j]){weight[j]=w;delta[j]=(forced[i]-natural[i])*w;}
            }
        }
        for(int i=0;i<n*n;i++){
            int x=i%n,z=i/n;var r=recipe.getAsJsonObject("river");
            boolean waterway=Math.abs(x-riverX(z))<number(r,"half_width")+number(r,"bank_width");
            heights[i]=forced[i]!=Integer.MIN_VALUE?forced[i]:(waterway?natural[i]:(int)Math.round(natural[i]+delta[i]));
        }
    }
    private void validate(){
        int max=integer(recipe.getAsJsonObject("terrain"),"max_earthwork"),water=integer(recipe.getAsJsonObject("river"),"water_y");
        for(int i=0;i<n*n;i++)if(Math.abs(heights[i]-natural[i])>max)throw new IllegalArgumentException("Earthwork budget exceeded at "+(i%n)+","+(i/n));
        for(int i=0;i<buildings.size();i++){
            Building a=buildings.get(i);
            for(int j=i+1;j<buildings.size();j++){Building b=buildings.get(j);if(a.x<b.x+b.width&&a.x+a.width>b.x&&a.z<b.z+b.depth&&a.z+a.depth>b.z)throw new IllegalArgumentException("Building overlap");}
            for(int z=a.pad[1];z<=a.pad[3];z++)for(int x=a.pad[0];x<=a.pad[2];x++)if(natural[z*n+x]<water)throw new IllegalArgumentException("Building in river");
        }
        for(var road:roads.values())for(var b:buildings)if(road.x>=b.x&&road.x<b.x+b.width&&road.z>=b.z&&road.z<b.z+b.depth)throw new IllegalArgumentException("Road cuts building "+b.id);
    }
    public JsonObject json(){
        Gson gson=new Gson();JsonObject out=new JsonObject();out.addProperty("schema",1);out.addProperty("algorithm",ALGORITHM);out.addProperty("seed",seed);out.addProperty("size",n);
        out.add("recipe",recipe.deepCopy());out.add("catalog",catalog.deepCopy());out.add("natural_heights",gson.toJsonTree(natural));out.add("heights",gson.toJsonTree(heights));out.add("kinds",gson.toJsonTree(kinds));
        out.add("buildings",gson.toJsonTree(buildings));out.add("clusters",gson.toJsonTree(clusters));out.add("routes",gson.toJsonTree(routes));out.add("roads",gson.toJsonTree(roads.values()));out.add("spawn",gson.toJsonTree(spawn));
        out.addProperty("height_semantics","first air above terrain; explicit road height is top of support block; bottom stairs retain half-step collision");
        out.addProperty("scope","offline bounded authored topology with seeded terrain and cluster offsets; not a general layout solver or runtime generator");return out;
    }
    public static void main(String[] args)throws Exception{
        if(args.length!=4)throw new IllegalArgumentException("seed recipe.json catalog.json out.json");
        var r=JsonParser.parseString(Files.readString(Path.of(args[1]))).getAsJsonObject();
        var c=JsonParser.parseString(Files.readString(Path.of(args[2]))).getAsJsonObject();
        var plan=new RegionPlan(Long.parseLong(args[0]),r,c);Files.writeString(Path.of(args[3]),new Gson().toJson(plan.json()));
    }
}
