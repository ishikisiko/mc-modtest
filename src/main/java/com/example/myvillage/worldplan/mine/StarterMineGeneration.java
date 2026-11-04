package com.example.myvillage.worldplan.mine;

import com.example.myvillage.worldplan.WorldPlan;
import com.example.myvillage.worldplan.roads.RoadLayout;
import java.util.Optional;

/** 矿场随生成器绑定一次；保存后使用原选址和完整接路数据。 */
public final class StarterMineGeneration {
    private final StarterMineLayout cached;
    private StarterMineLayout layout;
    private WorldPlan world;
    public StarterMineGeneration(Optional<StarterMineLayout> cached){this.cached=cached.orElse(null);}
    public void bind(WorldPlan world,RoadLayout roads){
        if(this.world!=null){if(!this.world.fingerprint().equals(world.fingerprint()))throw new IllegalStateException("矿场不能跨世界复用");return;}
        var def=world.rules().starterMine;
        var chosen=cached==null?StarterMinePlanner.plan(world,roads,def):cached;
        chosen.requireIdentity(world,roads,def);this.world=world;layout=chosen;
    }
    public StarterMineLayout layout(){if(layout==null)throw new IllegalStateException("矿场尚未完成世界绑定");return layout;}
    public StarterMineDefinition definition(){if(world==null)throw new IllegalStateException("矿场尚未完成世界绑定");return world.rules().starterMine;}
    public Optional<StarterMineLayout> snapshot(){return Optional.ofNullable(layout);}
    public boolean reserved(int x,int y,int z){return layout().reserved(world,definition(),x,y,z);}
    public boolean intersects(int x0,int y0,int z0,int x1,int y1,int z1){return layout().intersects(world,definition(),x0,y0,z0,x1,y1,z1);}
}
