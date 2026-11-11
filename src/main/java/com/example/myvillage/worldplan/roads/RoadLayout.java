package com.example.myvillage.worldplan.roads;

import com.example.myvillage.worldplan.WorldPlan;
import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.stream.IntStream;

/** Immutable, chunk-owned road columns. The complete layout is saved with the generator. */
public final class RoadLayout {
    public static final int FORMAT=3;
    private static final Codec<RoadJunctionPlanner.Hub> HUB_CODEC=RecordCodecBuilder.create(i->i.group(
        Codec.INT.fieldOf("x").forGetter(RoadJunctionPlanner.Hub::x),Codec.INT.fieldOf("z").forGetter(RoadJunctionPlanner.Hub::z),
        Codec.INT.fieldOf("feet2").forGetter(RoadJunctionPlanner.Hub::feet2),Codec.INT.fieldOf("radius").forGetter(RoadJunctionPlanner.Hub::radius),
        Codec.INT.fieldOf("branches").forGetter(RoadJunctionPlanner.Hub::branches)
    ).apply(i,RoadJunctionPlanner.Hub::new));
    public static final class Slice {
        public static final Codec<Slice> CODEC=RecordCodecBuilder.create(i->i.group(
            Codec.LONG.fieldOf("chunk").forGetter(s->s.key),
            Codec.INT_STREAM.fieldOf("columns").forGetter(s->Arrays.stream(s.codes))
        ).apply(i,(key,codes)->new Slice(key,codes.toArray())));
        public final long key;
        private final int[] codes,byLocal=new int[256];
        public Slice(long key,int[] codes){
            if(codes.length==0||codes.length>256)throw new IllegalArgumentException("道路区块列数量不合法");
            this.key=key;this.codes=codes.clone();Arrays.fill(byLocal,-1);
            for(int code:this.codes){if(code<0||(code>>>30)!=0)throw new IllegalArgumentException("道路列编码越界");int local=code&255;if(byLocal[local]!=-1)throw new IllegalArgumentException("道路区块列重复");byLocal[local]=code;decode(key,code);}
        }
        public int size(){return codes.length;}
        public List<RoadPlan.Cell> cells(){return Arrays.stream(codes).mapToObj(c->decode(key,c)).toList();}
        public RoadPlan.Cell at(int x,int z){if((int)key!=Math.floorDiv(x,16)||(int)(key>>>32)!=Math.floorDiv(z,16))return null;int code=byLocal[(x&15)|((z&15)<<4)];return code<0?null:decode(key,code);}
    }
    public static final class Route {
        public static final Codec<Route> CODEC=RecordCodecBuilder.create(i->i.group(
            Codec.STRING.fieldOf("from").forGetter(r->r.from),Codec.STRING.fieldOf("to").forGetter(r->r.to),
            Codec.INT.fieldOf("width").forGetter(r->r.width),Codec.STRING.fieldOf("sect_site").forGetter(r->r.sectSite),
            Codec.INT_STREAM.fieldOf("path").forGetter(r->Arrays.stream(r.path))
        ).apply(i,(from,to,width,site,path)->new Route(from,to,width,site,path.toArray())));
        public final String from,to,sectSite;
        public final int width;
        private final int[] path;
        public Route(String from,String to,int[] path){this(from,to,RoadRules.load().width,"",path);}
        public Route(String from,String to,int width,String site,int[] path){if(from.equals(to)||path.length<4||path.length%2!=0||path.length>200_000||width<3||width>9||width%2==0)throw new IllegalArgumentException("道路端点或路径不合法");this.from=from;this.to=to;this.width=width;sectSite=site;this.path=path.clone();}
        public String id(){return RoadNetworkGeometry.id(from,to);}
        public List<RoadAStar.Point> points(){List<RoadAStar.Point> p=new ArrayList<>();for(int j=0;j<path.length;j+=2)p.add(new RoadAStar.Point(path[j],path[j+1]));return List.copyOf(p);}
    }
    private record Stored(int format,String world,String rules,String hash,List<Slice> slices,List<Route> routes,List<RoadJunctionPlanner.Hub> hubs) {
        static final Codec<Stored> CODEC=RecordCodecBuilder.create(i->i.group(
            Codec.INT.fieldOf("format").forGetter(Stored::format),Codec.STRING.fieldOf("world_hash").forGetter(Stored::world),
            Codec.STRING.fieldOf("rules_hash").forGetter(Stored::rules),Codec.STRING.fieldOf("layout_hash").forGetter(Stored::hash),
            Slice.CODEC.listOf().fieldOf("chunks").forGetter(Stored::slices),Route.CODEC.listOf().fieldOf("routes").forGetter(Stored::routes),
            HUB_CODEC.listOf().fieldOf("hubs").forGetter(Stored::hubs)
        ).apply(i,Stored::new));
    }
    public static final Codec<RoadLayout> CODEC=Stored.CODEC.comapFlatMap(s->{
        try{if(s.format!=FORMAT)throw new IllegalArgumentException("道路生成格式不匹配");var value=new RoadLayout(s.world,s.rules,s.slices,s.routes,s.hubs);if(!value.hash.equals(s.hash))throw new IllegalArgumentException("道路布局摘要不匹配");return DataResult.success(value);}
        catch(IllegalArgumentException e){return DataResult.error(e::getMessage);}
    },v->new Stored(FORMAT,v.worldHash,v.rulesHash,v.hash,v.slices,v.routes,v.hubs));
    private final String worldHash,rulesHash,hash;
    private final List<Slice> slices;
    private final List<Route> routes;
    private final List<RoadJunctionPlanner.Hub> hubs;
    private final Long2ObjectMap<Slice> chunks;
    private final int columns;
    public RoadLayout(String world,String rules,List<Slice> slices,List<Route> routes){
        this(world,rules,slices,routes,List.of());
    }
    public RoadLayout(String world,String rules,List<Slice> slices,List<Route> routes,List<RoadJunctionPlanner.Hub> hubs){
        if(!world.matches("[0-9a-f]{64}")||!rules.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("道路身份摘要不合法");
        var limits=RoadRules.load();if(slices.size()>limits.maxNetworkChunks||routes.size()>limits.maxRoutes)throw new IllegalArgumentException("道路缓存超出配置预算");
        var by=new TreeMap<Long,Slice>();int total=0;for(var s:slices){if(by.putIfAbsent(s.key,s)!=null)throw new IllegalArgumentException("道路区块重复");total=Math.addExact(total,s.size());}
        if(total>limits.maxNetworkColumns)throw new IllegalArgumentException("道路列数量超出配置预算");
        var ids=new HashSet<String>();for(var r:routes){
            boolean valid=r.sectSite.isEmpty()?limits.ports.containsKey(r.from)&&limits.ports.containsKey(r.to)&&r.width==limits.width:
                r.from.equals(r.sectSite)&&limits.ports.containsKey(r.to)&&r.width==limits.sectSpurs.width();
            if(!valid||!ids.add(r.id()))throw new IllegalArgumentException("道路路线身份不合法");
        }
        if(slices.isEmpty()!=routes.isEmpty())throw new IllegalArgumentException("道路缓存缺少路幅或路线");
        if(hubs.size()>limits.junctions.maxNodes())throw new IllegalArgumentException("道路枢纽超出配置预算");
        var centers=new HashSet<Long>();for(var h:hubs)if(!centers.add(RoadPlan.Cell.key(h.x(),h.z()))||h.branches()<3||h.branches()>8||h.radius()<limits.junctions.minimumRadius()||h.radius()>limits.junctions.crossRadius())throw new IllegalArgumentException("道路枢纽记录不合法");
        this.worldHash=world;this.rulesHash=rules;this.slices=List.copyOf(by.values());this.routes=routes.stream().sorted(Comparator.comparing(Route::id)).toList();this.hubs=hubs.stream().sorted(Comparator.comparingInt(RoadJunctionPlanner.Hub::x).thenComparingInt(RoadJunctionPlanner.Hub::z)).toList();this.chunks=Long2ObjectMaps.unmodifiable(new Long2ObjectOpenHashMap<>(by));columns=total;
        for(var h:this.hubs)for(var c:RoadJunctionPlanner.pad(h,limits.junctions.cornerCut())){var actual=at(c.x(),c.z());if(actual==null||actual.band()>=2||actual.rail()!=-1||actual.feet2()!=h.feet2())throw new IllegalArgumentException("道路缓存缺少完整平坦枢纽");}
        hash=digest();
    }
    public static RoadLayout empty(WorldPlan world,RoadRules rules){return new RoadLayout(world.fingerprint(),rules.fingerprint,List.of(),List.of());}
    public static RoadLayout from(WorldPlan world,RoadRules rules,RoadNetworkPlanner.Result network){
        var grouped=new TreeMap<Long,List<Integer>>();for(var c:network.cells())if(!c.existing())grouped.computeIfAbsent(c.chunk(),k->new ArrayList<>()).add(pack(c));
        var slices=grouped.entrySet().stream().map(e->new Slice(e.getKey(),e.getValue().stream().mapToInt(Integer::intValue).toArray())).toList();
        var routes=network.routes().stream().map(r->{int[] p=new int[r.centerline().size()*2];for(int j=0;j<r.centerline().size();j++){p[j*2]=r.centerline().get(j).x();p[j*2+1]=r.centerline().get(j).z();}return new Route(r.from(),r.to(),r.width(),world.sectSite(r.from()).map(s->s.id()).orElse(""),p);}).toList();
        return new RoadLayout(world.fingerprint(),rules.fingerprint,slices,routes,network.hubs());
    }
    public static int pack(RoadPlan.Cell c){if(c.existing()||c.feet2()< -1024||c.feet2()>1023)throw new IllegalArgumentException("道路列不能编码");return (c.x()&15)|((c.z()&15)<<4)|((c.feet2()&2047)<<8)|(c.band()<<19)|((c.rail()+1)<<21);}
    private static RoadPlan.Cell decode(long key,int code){int feet=(code>>>8)&2047;if(feet>=1024)feet-=2048;int x=Math.addExact(Math.multiplyExact((int)key,16),code&15),z=Math.addExact(Math.multiplyExact((int)(key>>>32),16),(code>>>4)&15);return new RoadPlan.Cell(x,z,feet,false,(code>>>19)&3,((code>>>21)&511)-1);}
    public void requireIdentity(WorldPlan world,RoadRules rules){
        if(!worldHash.equals(world.fingerprint())||!rulesHash.equals(rules.fingerprint))throw new IllegalStateException("道路布局与当前世界或资源不一致，请使用当前开发版新世界");
        if(!rules.automatic)return;
        var reached=new HashSet<>(Set.of(world.cities().getFirst().regionId()));for(int n=0;n<world.cities().size();n++)for(var route:routes)if(route.sectSite.isEmpty()){if(reached.contains(route.from))reached.add(route.to);if(reached.contains(route.to))reached.add(route.from);}
        if(!reached.equals(rules.ports.keySet()))throw new IllegalStateException("缓存道路未连接全部省城");
        for(var border:RoadNetworkPlanner.progressionBorders(world))
            if(routes.stream().noneMatch(r->r.sectSite.isEmpty()&&Set.of(r.from,r.to).equals(Set.of(border.a(),border.b()))))
                throw new IllegalStateException("缓存道路缺少连续州之间的官道："+border.a()+"—"+border.b());
        var connected=new HashSet<String>();
        for(var route:routes)if(!route.sectSite.isEmpty()){
            var site=world.sectSite(route.sectSite).orElseThrow(()->new IllegalStateException("缓存支路引用未知宗门地块"));
            if(!route.to.equals(site.regionId())||!connected.add(site.id()))throw new IllegalStateException("宗门支路归属或重复项无效");
        }
        if(connected.size()!=world.sectSites().size())throw new IllegalStateException("缓存道路没有接通全部宗门山门");
        for(var route:routes)for(int i=0;i<route.path.length;i+=2)for(int dx=-route.width/2;dx<=route.width/2;dx++)for(int dz=-route.width/2;dz<=route.width/2;dz++){var c=at(route.path[i]+dx,route.path[i+1]+dz);if(c==null||c.band()>=2)throw new IllegalStateException("缓存道路缺少完整通行面");}
    }
    public String worldHash(){return worldHash;}public String rulesHash(){return rulesHash;}public String hash(){return hash;}public int columns(){return columns;}public List<Slice> slices(){return slices;}public List<Route> routes(){return routes;}public List<RoadJunctionPlanner.Hub> hubs(){return hubs;}
    public Slice slice(long key){return chunks.get(key);}
    public RoadPlan.Cell at(int x,int z){Slice s=chunks.get((Math.floorDiv(x,16)&0xffffffffL)|((long)Math.floorDiv(z,16)<<32));return s==null?null:s.at(x,z);}
    public boolean reserved(WorldPlan world,RoadRules rules,int x,int y,int z){var c=at(x,z);return c!=null&&y>=RoadPaver.low(world,rules,c)&&y<=RoadPaver.high(world,rules,c);}
    public boolean intersects(WorldPlan world,RoadRules rules,int x0,int y0,int z0,int x1,int y1,int z1){
        for(int cz=Math.floorDiv(z0,16);cz<=Math.floorDiv(z1,16);cz++)for(int cx=Math.floorDiv(x0,16);cx<=Math.floorDiv(x1,16);cx++){
            Slice slice=chunks.get((cx&0xffffffffL)|((long)cz<<32));if(slice==null)continue;
            for(var c:slice.cells())if(c.x()>=x0&&c.x()<=x1&&c.z()>=z0&&c.z()<=z1&&RoadPaver.low(world,rules,c)<=y1&&RoadPaver.high(world,rules,c)>=y0)return true;
        }return false;
    }
    public boolean permits(WorldPlan world,RoadRules rules,RoadPlan.Cell cell){for(var route:routes)if(WorldRoadPlanner.columnAllowed(world,rules,route.from,route.to,cell.x(),cell.z()))return true;return false;}
    private String digest(){
        try{var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);out.writeInt(FORMAT);out.writeUTF(worldHash);out.writeUTF(rulesHash);out.writeInt(slices.size());for(var s:slices){out.writeLong(s.key);out.writeInt(s.codes.length);for(int c:s.codes)out.writeInt(c);}out.writeInt(routes.size());for(var r:routes){out.writeUTF(r.from);out.writeUTF(r.to);out.writeInt(r.width);out.writeUTF(r.sectSite);out.writeInt(r.path.length);for(int p:r.path)out.writeInt(p);}out.writeInt(hubs.size());for(var h:hubs){out.writeInt(h.x());out.writeInt(h.z());out.writeInt(h.feet2());out.writeInt(h.radius());out.writeInt(h.branches());}out.flush();return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));}
        catch(IOException|NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
}
