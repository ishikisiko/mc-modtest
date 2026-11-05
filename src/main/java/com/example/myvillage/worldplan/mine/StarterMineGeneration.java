package com.example.myvillage.worldplan.mine;

import com.example.myvillage.worldplan.WorldPlan;
import com.example.myvillage.worldplan.roads.RoadLayout;
import java.util.*;
import java.util.function.BiFunction;
import net.minecraft.world.level.NoiseColumn;

/** 矿场随生成器绑定一次；保存后使用原选址和完整接路数据。 */
public final class StarterMineGeneration {
    private final StarterMineLayout cached;
    private StarterMineLayout layout;
    private WorldPlan world;
    private StarterMineCavePlan caves;
    private Optional<List<RegionalMineSite>> cachedRegional=Optional.empty();
    private List<RegionalMineSite> regional=List.of();
    public StarterMineGeneration(Optional<StarterMineLayout> cached){this.cached=cached.orElse(null);}
    public StarterMineGeneration(Optional<StarterMineLayout> cached,Optional<List<RegionalMineSite>> regional){this(cached);cachedRegional=regional.map(List::copyOf);}
    public void bindRegional(RoadLayout roads,BiFunction<Integer,Integer,NoiseColumn> columns){
        regional=cachedRegional.orElseGet(()->RegionalMinePlanner.plan(world,roads,layout,columns));
        List<RegionalMineSite> checked=new ArrayList<>();Map<String,Integer> counts=new HashMap<>();
        for(var site:regional){
            site.requireValid(definition());var s=site.surface();
            if(!s.worldHash.equals(world.fingerprint())||!s.roadsHash.equals(roads.hash())||!s.definitionHash.equals(definition().hash)||s.caveSeed!=StarterMineCavePlan.seed(world.seed(),s.x,s.z,definition().caves.salt))throw new IllegalArgumentException("普通矿口存档身份不一致");
            if(counts.merge(s.region,1,Integer::sum)>definition().regional.count)throw new IllegalArgumentException("普通矿口数量超出配置");
            RegionalMinePlanner.requireFootprint(world,roads,s,layout,checked,true);checked.add(site);
        }
    }
    public List<RegionalMineSite> regional(){return regional;}
    public Optional<List<RegionalMineSite>> regionalSnapshot(){return world==null?cachedRegional:Optional.of(regional);}
    public List<StarterMineLayout> surfaces(){var all=new ArrayList<StarterMineLayout>();all.add(layout());regional.forEach(s->all.add(s.surface()));return List.copyOf(all);}
    public void bind(WorldPlan world,RoadLayout roads){
        if(this.world!=null){if(!this.world.fingerprint().equals(world.fingerprint()))throw new IllegalStateException("矿场不能跨世界复用");return;}
        var def=world.rules().starterMine;
        var chosen=cached==null?StarterMinePlanner.plan(world,roads,def):cached;
        chosen.requireIdentity(world,roads,def);this.world=world;layout=chosen;caves=new StarterMineCavePlan(chosen.caveSeed,def);
    }
    public StarterMineLayout layout(){if(layout==null)throw new IllegalStateException("矿场尚未完成世界绑定");return layout;}
    public StarterMineDefinition definition(){if(world==null)throw new IllegalStateException("矿场尚未完成世界绑定");return world.rules().starterMine;}
    public StarterMineCavePlan caves(){if(caves==null)throw new IllegalStateException("洞穴尚未完成种子绑定");return caves;}
    public Optional<StarterMineLayout> snapshot(){return Optional.ofNullable(layout);}
    public boolean reserved(int x,int y,int z){return regional.stream().anyMatch(s->s.surface().reserved(world,definition(),x,y,z)||s.reserved(definition(),x,y,z))||layout().reserved(world,definition(),x,y,z)||caves().accessReserved(definition(),layout.localX(x,z),y-layout.y,layout.localZ(x,z));}
    public boolean intersects(int x0,int y0,int z0,int x1,int y1,int z1){return regional.stream().anyMatch(s->x0<=s.surface().maxX(definition())&&x1>=s.surface().minX(definition())&&z0<=s.surface().maxZ(definition())&&z1>=s.surface().minZ(definition())&&y1>=s.surface().y+s.floorY()-definition().caves.floorDepth&&y0<=s.surface().y+definition().height)||layout().intersects(world,definition(),x0,y0,z0,x1,y1,z1)||(x0<=layout.maxX(definition())&&x1>=layout.minX(definition())&&z0<=layout.maxZ(definition())&&z1>=layout.minZ(definition())&&y1>=layout.y+definition().caves.minY&&y0<=layout.y+definition().height);}
}
