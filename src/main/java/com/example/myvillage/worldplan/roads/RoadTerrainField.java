package com.example.myvillage.worldplan.roads;

import com.example.myvillage.worldplan.WorldPlan;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.Map;
import static com.example.myvillage.worldplan.roads.RoadPlan.Cell;

/** 接坡和主路共用地表、水深、完整路幅及固定地坪的采样规则。 */
abstract class RoadTerrainField implements RoadAStar.Field {
    private final WorldPlan world;
    private final RoadRules rules;
    private final int width;
    private final Map<Long,Cell> fixed;
    private final Map<Long,Integer> endpoints;
    private final Long2ObjectOpenHashMap<WorldRoadPlanner.Samples> tiles=new Long2ObjectOpenHashMap<>();
    RoadTerrainField(WorldPlan world,RoadRules rules,int width,Map<Long,Cell> fixed,Map<Long,Integer> endpoints) {
        this.world=world;this.rules=rules;this.width=width;this.fixed=fixed;this.endpoints=endpoints;
    }
    protected abstract boolean permitsColumn(int x,int z,int ground);
    protected abstract boolean inSearchArea(int x,int z);
    private WorldRoadPlanner.Samples tile(int x,int z) {
        long key=Cell.key(x>>4,z>>4);var tile=tiles.get(key);
        if(tile==null){if(tiles.size()>=rules.maxSampledTiles)throw new IllegalStateException("道路地形缓存达到配置上限");tile=new WorldRoadPlanner.Samples();tiles.put(key,tile);}
        return tile;
    }
    private static int at(int x,int z){return (z&15)*16+(x&15);}
    public final int ground(int x,int z){var t=tile(x,z);int i=at(x,z);if(t.heights[i]==Short.MIN_VALUE)t.heights[i]=(short)world.surfaceY(x,z);return t.heights[i];}
    public final int height(int x,int z){return Math.max(world.rules().seaLevel+1,ground(x,z));}
    public final int waterDepth(int x,int z){return Math.max(0,world.rules().seaLevel-ground(x,z));}
    public final int waterCostDepth(int x,int z){return Math.min(waterDepth(x,z),rules.bridgeCostDepth);}
    public final int corridorWidth(){return width;}
    public final boolean column(int x,int z){var t=tile(x,z);int i=at(x,z);if(t.columns[i]==0)t.columns[i]=(byte)(permitsColumn(x,z,ground(x,z))&&waterDepth(x,z)<=rules.bridgeMaxDepth?1:2);return t.columns[i]==1;}
    public final boolean allowed(int x,int z) {
        if(!inSearchArea(x,z))return false;
        var t=tile(x,z);int i=at(x,z);if(t.centers[i]!=0)return t.centers[i]==1;
        for(int dx=-width/2;dx<=width/2;dx++)for(int dz=-width/2;dz<=width/2;dz++)if(!column(x+dx,z+dz)){t.centers[i]=2;return false;}
        t.centers[i]=1;return true;
    }
    private WorldRoadPlanner.Samples grade(int x,int z) {
        var t=tile(x,z);int i=at(x,z);if(t.grades[i]!=0)return t;
        int low=Integer.MIN_VALUE,high=Integer.MAX_VALUE;
        for(int dx=-width/2;dx<=width/2;dx++)for(int dz=-width/2;dz<=width/2;dz++){
            int h=height(x+dx,z+dz)*2;low=Math.max(low,h-rules.maxEarthwork*2);high=Math.min(high,h+rules.maxEarthwork*2);
        }
        for(int dx=-width/2-1;dx<=width/2+1;dx++)for(int dz=-width/2-1;dz<=width/2+1;dz++){
            var old=fixed.get(Cell.key(x+dx,z+dz));if(old!=null){int d=Math.abs(dx)+Math.abs(dz);low=Math.max(low,old.feet2()-d);high=Math.min(high,old.feet2()+d);}
        }
        var exact=endpoints.get(Cell.key(x,z));if(exact!=null){low=Math.max(low,exact);high=Math.min(high,exact);}
        t.low[i]=(short)low;t.high[i]=(short)high;t.grades[i]=1;return t;
    }
    public final int minimumFeet2(int x,int z){return grade(x,z).low[at(x,z)];}
    public final int maximumFeet2(int x,int z){return grade(x,z).high[at(x,z)];}
}
