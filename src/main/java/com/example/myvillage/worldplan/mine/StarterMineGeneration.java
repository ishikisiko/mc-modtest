package com.example.myvillage.worldplan.mine;

import com.example.myvillage.worldplan.WorldPlan;
import com.example.myvillage.worldplan.roads.RoadLayout;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.chunk.ChunkAccess;

/** 矿场在区块生成前确定，存档保留已验证的布局或选址耗尽的结果。 */
public final class StarterMineGeneration {
    public record Shortfall(String worldHash,String roadsHash,String definitionHash,int approaches,int attempted,Map<String,Integer> rejections) {
        private static final Codec<Shortfall> CODEC=RecordCodecBuilder.create(i->i.group(
            Codec.STRING.fieldOf("world_hash").forGetter(Shortfall::worldHash),
            Codec.STRING.fieldOf("roads_hash").forGetter(Shortfall::roadsHash),
            Codec.STRING.fieldOf("definition_hash").forGetter(Shortfall::definitionHash),
            Codec.INT.fieldOf("approaches").forGetter(Shortfall::approaches),
            Codec.INT.fieldOf("attempted").forGetter(Shortfall::attempted),
            Codec.unboundedMap(Codec.STRING,Codec.intRange(1,2048)).fieldOf("rejections").forGetter(Shortfall::rejections)
        ).apply(i,Shortfall::new));

        public Shortfall {
            rejections=Collections.unmodifiableMap(new TreeMap<>(rejections));
        }

        void requireIdentity(WorldPlan world,RoadLayout roads,StarterMineDefinition def){
            if(!worldHash.equals(world.fingerprint())||!roadsHash.equals(roads.hash())||!definitionHash.equals(def.hash))
                throw new IllegalStateException("矿场选址结果与当前世界、道路或资源身份不一致");
            long variants=(long)def.placement.connectorLengths().size()*def.placement.lateralOffsets().size();
            long expected=Math.min(approaches,def.placement.attempts()/variants)*variants;
            if(approaches<0||attempted!=expected||rejections.values().stream().mapToLong(Integer::longValue).sum()!=attempted)
                throw new IllegalArgumentException("矿场选址记录的候选数量不一致");
        }
    }

    public static final Codec<Either<StarterMineLayout,Shortfall>> CODEC=Codec.either(StarterMineLayout.CODEC,Shortfall.CODEC);

    private final Optional<Either<StarterMineLayout,Shortfall>> cached;
    private final Optional<List<RegionalMineSite>> cachedRegional;
    private Either<StarterMineLayout,Shortfall> selection;
    private WorldPlan world;
    private StarterMineCavePlan caves;
    private final Map<Long,Set<BlockPos>> caveAirByChunk=new ConcurrentHashMap<>();
    private List<RegionalMineSite> regional=List.of();

    public StarterMineGeneration(Optional<Either<StarterMineLayout,Shortfall>> cached,Optional<List<RegionalMineSite>> regional){
        this.cached=cached;
        cachedRegional=regional.map(List::copyOf);
    }

    public void bind(WorldPlan world,RoadLayout roads){
        if(this.world!=null){
            if(!this.world.fingerprint().equals(world.fingerprint()))throw new IllegalStateException("矿场不能跨世界复用");
            return;
        }
        var def=world.rules().starterMine;
        var result=cached.orElseGet(()->plan(world,roads,def));
        result.left().ifPresent(layout->layout.requireIdentity(world,roads,def));
        result.right().ifPresent(shortfall->shortfall.requireIdentity(world,roads,def));
        caves=result.left().map(layout->new StarterMineCavePlan(layout.caveSeed,def)).orElse(null);
        selection=result;
        this.world=world;
    }

    private static Either<StarterMineLayout,Shortfall> plan(WorldPlan world,RoadLayout roads,StarterMineDefinition def){
        var search=StarterMinePlanner.search(world,roads,def);
        var logger=org.slf4j.LoggerFactory.getLogger(StarterMineGeneration.class);
        if(search.layout().isPresent()){
            var layout=search.layout().orElseThrow();
            logger.info("开局矿场 seed={}，接出点={}，第 {} 个候选采用，位置=({}, {}, {})",
                world.seed(),search.approaches(),search.attempted(),layout.x,layout.y,layout.z);
            return Either.left(layout);
        }
        logger.warn("开局矿场选址用尽，主世界继续生成：seed={}，接出点={}，检查候选={}，拒绝原因={}",
            world.seed(),search.approaches(),search.attempted(),search.rejections());
        return Either.right(new Shortfall(world.fingerprint(),roads.hash(),def.hash,search.approaches(),search.attempted(),search.rejections()));
    }

    public void bindRegional(RoadLayout roads,BiFunction<Integer,Integer,NoiseColumn> columns){
        var starter=layout();
        var chosen=cachedRegional.orElseGet(()->RegionalMinePlanner.plan(world,roads,starter,columns));
        var def=definition();
        List<RegionalMineSite> checked=new ArrayList<>();
        Map<String,Integer> counts=new HashMap<>();
        for(var site:chosen){
            site.requireValid(def);
            var s=site.surface();
            if(!s.worldHash.equals(world.fingerprint())||!s.roadsHash.equals(roads.hash())||!s.definitionHash.equals(def.hash)
                ||s.caveSeed!=StarterMineCavePlan.seed(world.seed(),s.x,s.z,def.caves.salt))
                throw new IllegalArgumentException("普通矿口存档身份不一致");
            if(counts.merge(s.region,1,Integer::sum)>def.regional.count)throw new IllegalArgumentException("普通矿口数量超出配置");
            RegionalMinePlanner.requireFootprint(world,roads,s,starter,checked,true);
            RegionalMinePlanner.requireAccess(world,roads,site,starter,checked);
            checked.add(site);
        }
        regional=chosen;
    }

    public List<RegionalMineSite> regional(){return regional;}
    public Optional<List<RegionalMineSite>> regionalSnapshot(){return world==null?cachedRegional:Optional.of(regional);}
    public Optional<Either<StarterMineLayout,Shortfall>> snapshot(){return selection==null?cached:Optional.of(selection);}
    public Optional<StarterMineLayout> layout(){return current().left();}
    public Optional<Shortfall> shortfall(){return current().right();}
    private Either<StarterMineLayout,Shortfall> current(){
        if(selection==null)throw new IllegalStateException("矿场尚未完成世界绑定");
        return selection;
    }
    public StarterMineDefinition definition(){
        if(world==null)throw new IllegalStateException("矿场尚未完成世界绑定");
        return world.rules().starterMine;
    }
    public StarterMineCavePlan caves(){
        if(caves==null)throw new IllegalStateException("当前世界没有开局矿场洞穴");
        return caves;
    }

    /** Reuse the carved shape so nearby natural caves and the surface keep their regional rules. */
    public boolean containsCaveAir(BlockPos pos,Supplier<ChunkAccess> loadedChunk){
        if(world==null||caves==null)return false;
        var layout=layout().orElseThrow();
        var def=definition();
        if(pos.getY()<layout.y+def.caves.minY||!layout.inside(def,pos.getX(),pos.getZ()))return false;
        long key=ChunkPos.asLong(pos.getX()>>4,pos.getZ()>>4);
        var air=caveAirByChunk.computeIfAbsent(key,ignored->{
            var chunk=loadedChunk.get();
            return chunk==null?null:StarterMineCaveCarver.shape(world,this,chunk).air();
        });
        return air!=null&&air.contains(pos);
    }

    public List<StarterMineLayout> surfaces(){
        var all=new ArrayList<StarterMineLayout>();
        layout().ifPresent(all::add);
        regional.forEach(s->all.add(s.surface()));
        return List.copyOf(all);
    }

    public boolean reserved(int x,int y,int z){
        var def=definition();
        if(regional.stream().anyMatch(s->s.surface().reserved(world,def,x,y,z)||s.reserved(def,x,y,z)))return true;
        return layout().map(s->s.reserved(world,def,x,y,z)||caves().accessReserved(def,s.localX(x,z),y-s.y,s.localZ(x,z))).orElse(false);
    }

    public boolean intersects(int x0,int y0,int z0,int x1,int y1,int z1){
        var def=definition();
        if(regional.stream().anyMatch(s->s.intersects(world,def,x0,y0,z0,x1,y1,z1)))return true;
        return layout().map(s->s.intersects(world,def,x0,y0,z0,x1,y1,z1)
            ||x0<=s.maxX(def)&&x1>=s.minX(def)&&z0<=s.maxZ(def)&&z1>=s.minZ(def)&&y1>=s.y+def.caves.minY&&y0<=s.y+def.height).orElse(false);
    }
}
