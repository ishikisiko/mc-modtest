package com.example.myvillage.worldplan.runtime;

import com.example.myvillage.worldplan.*;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import java.util.*;

/** Explicit bounded developer acceptance commands; ordinary queries never request chunks. */
public final class WorldPlanCommands {
    private WorldPlanCommands() {}
    public static void register(RegisterCommandsEvent e){
        e.getDispatcher().register(Commands.literal("myvillage").requires(s->s.hasPermission(2))
            .then(Commands.literal("worldplan")
                .then(Commands.literal("info").executes(c->info(c.getSource())))
                .then(Commands.literal("city").executes(c->cities(c.getSource(),""))
                    .then(Commands.argument("region",StringArgumentType.word())
                        .suggests((c,b)->{for(var region:com.example.myvillage.worldplan.capital.CapitalRegion.values())b.suggest(region.id());return b.buildFuture();})
                        .executes(c->cities(c.getSource(),StringArgumentType.getString(c,"region")))))
                .then(Commands.literal("settlements").executes(c->info(c.getSource())))
                .then(Commands.literal("qianzhen").executes(c->info(c.getSource())))
                .then(Commands.literal("at").then(Commands.argument("x",IntegerArgumentType.integer(-29999984,29999984))
                    .then(Commands.argument("z",IntegerArgumentType.integer(-29999984,29999984))
                        .executes(c->at(c.getSource(),IntegerArgumentType.getInteger(c,"x"),IntegerArgumentType.getInteger(c,"z"))))))
                .then(Commands.literal("export").executes(c->export(c.getSource())))
                .then(Commands.literal("audit_city").executes(c->audit(c.getSource())))
                .then(Commands.literal("pregen_city").then(Commands.argument("order",StringArgumentType.word())
                    .suggests((c,b)->{b.suggest("forward");b.suggest("reverse");return b.buildFuture();})
                    .executes(c->pregen(c.getSource(),StringArgumentType.getString(c,"order"),35))
                    .then(Commands.argument("limit",IntegerArgumentType.integer(1,35)).executes(c->pregen(c.getSource(),StringArgumentType.getString(c,"order"),IntegerArgumentType.getInteger(c,"limit"))))))
                .then(Commands.literal("probe").then(Commands.argument("chunk_x",IntegerArgumentType.integer(-1874999,1874999))
                    .then(Commands.argument("chunk_z",IntegerArgumentType.integer(-1874999,1874999))
                        .executes(c->probe(c.getSource(),IntegerArgumentType.getInteger(c,"chunk_x"),IntegerArgumentType.getInteger(c,"chunk_z"))))))));
    }
    private static PlannedChunkGenerator generator(CommandSourceStack s){
        return WorldPlanRuntime.generator(s.getLevel()).orElseThrow(()->new IllegalArgumentException("当前世界未使用修仙大陆预设；已有世界不会自动转换。"));
    }
    private static int reply(CommandSourceStack s,JsonObject o){s.sendSuccess(()->Component.literal("WORLDPLAN "+WorldPlanRules.JSON.toJson(o)),false);return 1;}
    private static int fail(CommandSourceStack s,Exception e){s.sendFailure(Component.literal("大陆蓝图："+e.getMessage()));return 0;}
    private static int cities(CommandSourceStack s,String region){try {
        var p=generator(s).plan();
        if(p.rules().capitals==null) {
            var c=p.city();
            if(!region.isEmpty()&&!region.equals(c.host())&&!region.equals(p.rules().profile(c.host()).name()))throw new IllegalArgumentException("旧版世界仅保留云山城。");
            int x=c.x()+c.width()/2,z=c.z()+c.depth()-4,y=c.baseY()+p.rules().city.surface(c.width()/2,c.depth()-4);
            s.sendSuccess(()->Component.literal("云山城｜"+p.rules().profile(c.host()).name()+"｜入口 "+x+" "+y+" "+z+"（旧版规划）"),false);return 1;
        }
        var selected=p.cities().stream().filter(c->region.isEmpty()||c.regionId().equals(region)||c.cityId().equals(region)||p.rules().profile(c.regionId()).name().equals(region)).toList();
        if(selected.isEmpty())throw new IllegalArgumentException("未找到该州主城；可输入 zhongzhou、donghai、ximo、nanjiang、beiyuan、donghuang、lingyue、moyu。");
        for(var c:selected) {
            var at=c.entrance();
            var condition=com.example.myvillage.sim.runtime.life.NpcLifeSites.availability(s.getLevel(),
                com.example.myvillage.sim.life.LifePlace.free("entry","entry",at.x(),at.y(),at.z()));
            String state=s.getServer().getWorldData().worldGenOptions().generateStructures()
                ?switch(condition){case AVAILABLE->"可站立";case BLOCKED->"入口受阻";case UNLOADED->"尚未加载";}:"结构生成已关闭";
            String line=c.bundle().displayName+"｜"+p.rules().profile(c.regionId()).name()+"｜入口 "+at.x()+" "+at.y()+" "+at.z()+"（"+state+"）";
            s.sendSuccess(()->Component.literal(line),false);
        }
        return selected.size();
    }catch(Exception e){return fail(s,e);}}
    private static int info(CommandSourceStack s){try{
        var p=generator(s).plan();JsonObject o=new JsonObject();o.addProperty("active",true);o.addProperty("seed",Long.toString(p.seed()));
        o.addProperty("algorithm",p.rules().algorithm);
        o.addProperty("plan_sha256",p.fingerprint());o.addProperty("rules_sha256",p.rules().fingerprint());
        if(p.rules().capitals==null)o.add("city",WorldPlanRules.JSON.toJsonTree(p.city()));else {
            o.add("cities",p.manifest().get("cities"));var life=new com.google.gson.JsonArray();
            com.example.myvillage.sim.runtime.WorldSimRuntime.sim().ifPresent(sim->{for(var city:p.cities())life.add(com.example.myvillage.sim.runtime.life.NpcLifeSites.capitalStatus(s.getServer(),sim,city));});
            o.add("life",life);
        }
        o.add("settlements",WorldPlanRules.JSON.toJsonTree(p.settlements()));
        o.add("qianzhen",WorldPlanRules.JSON.toJsonTree(p.qianzhen()));o.add("qianzhen_buildings",WorldPlanRules.JSON.toJsonTree(p.qianzhenBuildings()));
        o.add("spawn",WorldPlanRules.JSON.toJsonTree(p.spawn()));o.addProperty("structures_enabled",s.getServer().getWorldData().worldGenOptions().generateStructures());
        o.addProperty("atlas_directory",WorldPlanRuntime.directory(s.getLevel()).toString());return reply(s,o);
    }catch(Exception e){return fail(s,e);}}
    private static int at(CommandSourceStack s,int x,int z){try{return reply(s,WorldPlanRules.JSON.toJsonTree(generator(s).plan().sample(x,z)).getAsJsonObject());}catch(Exception e){return fail(s,e);}}
    private static int export(CommandSourceStack s){try{WorldPlanRuntime.export(s.getLevel(),256);return info(s);}catch(Exception e){return fail(s,e);}}
    private static int pregen(CommandSourceStack s,String order,int limit){try{
        if(!order.equals("forward")&&!order.equals("reverse"))throw new IllegalArgumentException("Order must be forward or reverse");
        var p=generator(s).plan();
        if(p.rules().capitals!=null) {
            var positions=new ArrayList<net.minecraft.core.BlockPos>();
            for(var city:p.cities())for(var tile:city.bundle().tiles)positions.add(new net.minecraft.core.BlockPos(city.x()+tile.x(),city.originY(),city.z()+tile.z()));
            if(order.equals("reverse"))Collections.reverse(positions);
            for(var at:positions.subList(0,Math.min(limit,positions.size())))s.getLevel().getChunkSource().getChunk(at.getX()>>4,at.getZ()>>4,ChunkStatus.FULL,true);
            JsonObject o=new JsonObject();o.addProperty("requested",Math.min(limit,positions.size()));o.addProperty("order",order);o.addProperty("permanent_tickets_added",0);return reply(s,o);
        }
        var tiles=new ArrayList<>(p.rules().city.tiles());if(order.equals("reverse"))Collections.reverse(tiles);
        for(var tile:tiles.subList(0,Math.min(limit,tiles.size())))s.getLevel().getChunkSource().getChunk((p.city().x()+tile.x())>>4,(p.city().z()+tile.z())>>4,ChunkStatus.FULL,true);
        JsonObject o=new JsonObject();o.addProperty("requested",Math.min(limit,tiles.size()));o.addProperty("order",order);o.addProperty("permanent_tickets_added",0);return reply(s,o);
    }catch(Exception e){return fail(s,e);}}
    private static int audit(CommandSourceStack s){try{return reply(s,PlannedCity.audit(generator(s).plan(),s.getLevel()));}catch(Exception e){return fail(s,e);}}
    private static int probe(CommandSourceStack s,int cx,int cz){try{
        var g=generator(s);ServerLevel level=s.getLevel();ChunkAccess chunk=level.getChunkSource().getChunk(cx,cz,ChunkStatus.NOISE,true);
        if(chunk==null)throw new IllegalStateException("NOISE chunk request failed");
        boolean exact=chunk.getPersistedStatus()==ChunkStatus.NOISE;int mismatch=0,columns=0;StringBuilder signature=new StringBuilder();
        for(int z=0;z<16;z++)for(int x=0;x<16;x++){
            int wx=cx*16+x,wz=cz*16+z,target=g.plan().surfaceY(wx,wz);int actual=chunk.getHeight(Heightmap.Types.OCEAN_FLOOR_WG,x,z)+1;
            if(exact&&actual!=target)mismatch++;
            int predicted=g.getBaseHeight(wx,wz,Heightmap.Types.OCEAN_FLOOR_WG,level,g.terrainState());
            if(predicted!=target)throw new IllegalStateException("Base-height contract mismatch");
            signature.append(wx).append(',').append(wz).append(':').append(actual).append(';');columns++;
        }
        JsonObject o=new JsonObject();o.addProperty("chunk_x",cx);o.addProperty("chunk_z",cz);o.addProperty("status",chunk.getPersistedStatus().toString());
        o.addProperty("exact_noise_stage",exact);o.addProperty("columns",columns);o.addProperty("height_mismatches",mismatch);
        o.addProperty("height_sha256",WorldPlanRules.sha256(signature.toString()));o.addProperty("plan_sha256",g.plan().fingerprint());return reply(s,o);
    }catch(Exception e){return fail(s,e);}}
}
