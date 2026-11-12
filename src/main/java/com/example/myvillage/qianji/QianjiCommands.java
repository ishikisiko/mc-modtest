package com.example.myvillage.qianji;

import com.example.myvillage.worldplan.capital.CapitalCity;
import com.example.myvillage.worldplan.runtime.WorldPlanRuntime;
import com.google.gson.*;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import java.util.*;
import net.minecraft.commands.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.*;

/** 管理员的实际碰撞读回与动效相位检查；不提供玩家侧状态写入入口。 */
public final class QianjiCommands {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher){
        dispatcher.register(Commands.literal("myvillage").requires(s->s.hasPermission(2)).then(Commands.literal("qianji")
                .then(Commands.literal("probe").executes(c->probe(c.getSource())))
                .then(Commands.literal("phase").then(Commands.argument("seconds",DoubleArgumentType.doubleArg(0,3600)).executes(c->phase(c.getSource(),DoubleArgumentType.getDouble(c,"seconds")))))));
    }
    private static CapitalCity city(ServerLevel level){return WorldPlanRuntime.generator(level).orElseThrow(()->new IllegalArgumentException("当前维度没有修仙大陆规划")).plan().capitalOf("zhongzhou").orElseThrow();}
    private static double a(JsonArray p,int i){return p.get(i).getAsDouble();}
    private static Vec3 position(CapitalCity city,JsonArray p,boolean center){var entry=QianjiCatalog.get().layout.getAsJsonArray("entry");var q=city.bundle().qianji.entry();double shift=center?.75:0;return new Vec3(city.x()+q.x()+a(entry,0)-a(p,0)+shift,city.originY()+q.y()-a(entry,1)+a(p,1),city.z()+q.z()+a(entry,2)-a(p,2)+shift);}
    private static OptionalDouble floor(ServerLevel level,Vec3 p){
        List<double[]> spans=new ArrayList<>();double half=.3-1e-5;
        for(int x=(int)Math.floor(p.x-half);x<=Math.floor(p.x+half);x++)for(int z=(int)Math.floor(p.z-half);z<=Math.floor(p.z+half);z++)for(int y=(int)Math.floor(p.y)-3;y<=Math.ceil(p.y)+3;y++){
            BlockPos pos=new BlockPos(x,y,z);for(var b:level.getBlockState(pos).getCollisionShape(level,pos).toAabbs())if(x+b.maxX>p.x-half&&x+b.minX<p.x+half&&z+b.maxZ>p.z-half&&z+b.minZ<p.z+half)spans.add(new double[]{y+b.minY,y+b.maxY});
        }
        spans.sort(Comparator.comparingDouble(x->x[0]));List<double[]> merged=new ArrayList<>();
        for(var s:spans){if(!merged.isEmpty()&&s[0]<=merged.getLast()[1]+1e-6)merged.getLast()[1]=Math.max(merged.getLast()[1],s[1]);else merged.add(s.clone());}
        double best=Double.NaN,distance=Double.POSITIVE_INFINITY;
        for(int i=0;i<merged.size();i++){double y=merged.get(i)[1];if(i+1<merged.size()&&merged.get(i+1)[0]-y<1.8-1e-5)continue;double delta=Math.abs(y-p.y);if(delta<=1.25&&delta<distance){best=y;distance=delta;}}
        return Double.isNaN(best)?OptionalDouble.empty():OptionalDouble.of(best);
    }
    private static int probe(CommandSourceStack source){try{
        var level=source.getLevel();var city=city(level);JsonArray rows=new JsonArray();boolean all=true;
        for(var value:QianjiCatalog.get().layout.getAsJsonArray("targets")){
            var t=value.getAsJsonObject();Vec3 p=position(city,t.getAsJsonArray("position"),true);JsonObject row=new JsonObject();row.addProperty("id",t.get("id").getAsString());boolean loaded=level.hasChunkAt(BlockPos.containing(p));row.addProperty("loaded",loaded);var floor=loaded?floor(level,p):OptionalDouble.empty();boolean clear=false;
            if(floor.isPresent()){double y=floor.getAsDouble();AABB body=new AABB(p.x-.3+1e-5,y+1e-4,p.z-.3+1e-5,p.x+.3-1e-5,y+1.8,p.z+.3-1e-5);clear=!level.getBlockCollisions(null,body).iterator().hasNext();row.add("feet",new Gson().toJsonTree(new double[]{p.x,y,p.z}));row.addProperty("block_light",level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK,BlockPos.containing(p.x,y+1,p.z)));}
            row.addProperty("standing",floor.isPresent()&&clear);all&=floor.isPresent()&&clear;rows.add(row);
        }
        Vec3 pos=position(city,QianjiCatalog.get().layout.getAsJsonArray("anchor"),false);BlockPos anchor=BlockPos.containing(pos);boolean bound=level.hasChunkAt(anchor)&&level.getBlockEntity(anchor) instanceof QianjiAtmosphereEntity;JsonObject result=new JsonObject();result.addProperty("all_standing",all);result.addProperty("anchor_bound",bound);result.add("anchor",new Gson().toJsonTree(new int[]{anchor.getX(),anchor.getY(),anchor.getZ()}));result.addProperty("game_time",level.getGameTime());result.add("targets",rows);source.sendSuccess(()->Component.literal(result.toString()),false);return all&&bound?1:0;
    }catch(Exception e){source.sendFailure(Component.literal("千机阁读回失败："+e.getMessage()));return 0;}}
    private static int phase(CommandSourceStack source,double seconds){try{
        var level=source.getLevel();var r=QianjiAtmosphereRules.get();if(seconds>=r.number("cycle_seconds"))throw new IllegalArgumentException("相位超过当前周期");var city=city(level);BlockPos p=BlockPos.containing(position(city,QianjiCatalog.get().layout.getAsJsonArray("anchor"),false));if(!(level.getBlockEntity(p) instanceof QianjiAtmosphereEntity anchor))throw new IllegalArgumentException("总舵阵枢尚未加载");anchor.setCycleOffset(Math.round(seconds*20)-Math.floorMod(level.getGameTime(),r.cycleTicks()));source.sendSuccess(()->Component.literal("千机阁动效相位已设置为 "+seconds+" 秒，继续随世界时间运行。"),false);return 1;
    }catch(Exception e){source.sendFailure(Component.literal(e.getMessage()));return 0;}}
    private QianjiCommands(){}
}
