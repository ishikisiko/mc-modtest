package com.example.myvillage.compat.waystones;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.worldplan.capital.CapitalCity;
import com.example.myvillage.worldplan.capital.CapitalWaystone;
import com.example.myvillage.worldplan.runtime.WorldPlanRuntime;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import java.util.ArrayList;
import java.util.List;
import net.blay09.mods.waystones.api.WaystonesAPI;
import net.blay09.mods.waystones.block.entity.WaystoneBlockEntityBase;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** 隔离新世界中的区块生成及回读探针，不是玩家操作证据，不随 JAR 打包。 */
@EventBusSubscriber(modid=MyVillageMod.MOD_ID)
public final class CapitalWaystonesProbe {
    private record Target(CapitalCity city, CapitalWaystone site, BlockPos pos) { }
    private static List<Target> targets(ServerLevel level) {
        var result=new ArrayList<Target>();
        for(var city:WorldPlanRuntime.generator(level).orElseThrow().plan().cities())
            for(var s:city.bundle().waystones)
                result.add(new Target(city,s,new BlockPos(city.x()+s.x(),city.originY()+s.y(),city.z()+s.z())));
        return result;
    }
    @SubscribeEvent static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("capitalstonesprobe").requires(s->s.hasPermission(2))
            .then(Commands.literal("list").executes(c->{
                var out=new JsonObject();var rows=new JsonArray();var level=c.getSource().getServer().overworld();
                for(var t:targets(level)){var row=new JsonObject();row.addProperty("id",t.site.id());row.addProperty("name",t.site.name());row.addProperty("x",t.pos.getX());row.addProperty("y",t.pos.getY());row.addProperty("z",t.pos.getZ());rows.add(row);}
                out.add("sites",rows);send(c.getSource(),out);return rows.size();
            }))
            .then(Commands.literal("inspect").then(Commands.argument("index",IntegerArgumentType.integer(0,24)).executes(c->{
                send(c.getSource(),inspect(c.getSource().getServer().overworld(),IntegerArgumentType.getInteger(c,"index")));return 1;
            })))
            .then(Commands.literal("mark").then(Commands.argument("index",IntegerArgumentType.integer(0,24)).executes(c->{
                var level=c.getSource().getServer().overworld();var t=targets(level).get(IntegerArgumentType.getInteger(c,"index"));
                level.setBlockAndUpdate(t.pos,Blocks.GOLD_BLOCK.defaultBlockState());
                send(c.getSource(),inspect(level,IntegerArgumentType.getInteger(c,"index")));return 1;
            })))
            .then(Commands.literal("registered").executes(c->{
                var out=new JsonObject();var rows=new JsonArray();var level=c.getSource().getServer().overworld();
                var names=targets(level).stream().map(t->t.site.name()).toList();
                WaystonesAPI.getAllWaystones(level.getServer()).filter(w->names.contains(w.getName().getString())).forEach(w->{var r=new JsonObject();r.addProperty("name",w.getName().getString());r.addProperty("uuid",w.getWaystoneUid().toString());rows.add(r);});
                out.add("registered",rows);send(c.getSource(),out);return rows.size();
            })));
    }
    private static void send(net.minecraft.commands.CommandSourceStack source,JsonObject data) {
        source.sendSuccess(()->Component.literal("CAPITAL_WAYSTONES_PROBE "+data),false);
    }
    private static JsonObject inspect(ServerLevel level,int index) {
        var t=targets(level).get(index);var pos=t.pos;
        for(int cx=(pos.getX()-7)>>4;cx<=(pos.getX()+7)>>4;cx++)
            for(int cz=(pos.getZ()-7)>>4;cz<=(pos.getZ()+7)>>4;cz++)level.getChunk(cx,cz);
        var out=new JsonObject();out.addProperty("id",t.site.id());out.addProperty("expected_name",t.site.name());out.addProperty("x",pos.getX());out.addProperty("y",pos.getY());out.addProperty("z",pos.getZ());
        var status=level.getChunkAt(pos).getFullStatus();
        out.addProperty("full_status",status.name());
        // 远程 FULL 查询只保证区块存在；过早 getBlockEntity 会主动提升尚未加载的 NBT。
        // 等待真实更新区块完成自己的 BE 生命周期，探针不能代替 onLoad 初始化。
        if(status!=net.minecraft.server.level.FullChunkStatus.ENTITY_TICKING)return out;
        out.addProperty("lower",level.getBlockState(pos).toString());out.addProperty("upper",level.getBlockState(pos.above()).toString());
        out.addProperty("floor",level.getBlockState(pos.below()).toString());out.addProperty("marker",level.getBlockState(pos).is(Blocks.GOLD_BLOCK));
        var lower=level.getBlockEntity(pos);var upper=level.getBlockEntity(pos.above());
        out.addProperty("lower_be",lower instanceof WaystoneBlockEntityBase);out.addProperty("upper_be",upper instanceof WaystoneBlockEntityBase);
        if(lower instanceof WaystoneBlockEntityBase a && upper instanceof WaystoneBlockEntityBase b){
            out.addProperty("lower_valid",a.getWaystone().isValid());out.addProperty("upper_valid",b.getWaystone().isValid());
            out.addProperty("valid",a.getWaystone().isValid()&&b.getWaystone().isValid());
            if(!a.getWaystone().isValid()||!b.getWaystone().isValid()){
                out.addProperty("lower_nbt",a.saveWithFullMetadata(level.registryAccess()).toString());
                out.addProperty("upper_nbt",b.saveWithFullMetadata(level.registryAccess()).toString());
            }
            if(a.getWaystone().isValid()&&b.getWaystone().isValid()){
                out.addProperty("uuid",a.getWaystone().getWaystoneUid().toString());out.addProperty("upper_uuid",b.getWaystone().getWaystoneUid().toString());out.addProperty("name",a.getWaystone().getName().getString());
                out.addProperty("registered",WaystonesAPI.getWaystoneAt(level,pos).isPresent());
                out.addProperty("origin",a.getWaystone().getOrigin().name());
            }
        }
        int clear=0,supported=0;
        for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++){
            if(!level.getBlockState(pos.offset(dx,-1,dz)).isAir())supported++;
            for(int dy=0;dy<4;dy++)if((dx!=0||dz!=0||dy>=2)&&level.getBlockState(pos.offset(dx,dy,dz)).isAir())clear++;
        }
        out.addProperty("arrival_supported",supported);out.addProperty("arrival_clear",clear);
        return out;
    }
}
