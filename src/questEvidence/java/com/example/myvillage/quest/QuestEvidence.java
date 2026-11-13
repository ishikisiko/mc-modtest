package com.example.myvillage.quest;

import com.example.myvillage.sim.runtime.economy.*;
import com.example.myvillage.sim.runtime.WorldSimRuntime;
import com.example.myvillage.worldplan.runtime.WorldPlanRuntime;
import com.example.myvillage.worldplan.capital.CapitalCity;
import com.google.gson.*;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Opt-in fixtures only. Commands never accept quests, deal damage, finish or pay rewards. */
@EventBusSubscriber(modid="myvillage")
public final class QuestEvidence {
    @SubscribeEvent public static void commands(RegisterCommandsEvent e){
        e.getDispatcher().register(Commands.literal("questfixture").requires(s->s.hasPermission(2))
            .then(Commands.literal("board").then(Commands.argument("player",EntityArgument.player()).then(Commands.argument("region",StringArgumentType.word()).executes(c->{
                var p=EntityArgument.getPlayer(c,"player");String region=StringArgumentType.getString(c,"region");
                var city=WorldPlanRuntime.generator(p.getServer().overworld()).orElseThrow().plan().cities().stream().filter(v->v.regionId().equals(region)).findFirst().orElseThrow();
                var point=city.bundle().life().getAsJsonArray("places").asList().stream().map(JsonElement::getAsJsonObject).filter(o->o.get("id").getAsString().equals("jianghu_board")).findFirst().orElseThrow();
                BlockPos board=position(city,point,"block_pos"),reader=position(city,point,"pos");var level=p.getServer().overworld();
                level.getChunk(board);level.getChunk(reader);p.teleportTo(level,reader.getX()+.5,reader.getY(),reader.getZ()+.5,java.util.Set.of(),0,0);
                var source=new JsonObject();source.addProperty("city",city.cityId());source.addProperty("region",city.regionId());source.add("board",new Gson().toJsonTree(new int[]{board.getX(),board.getY(),board.getZ()}));source.add("reader",new Gson().toJsonTree(new int[]{reader.getX(),reader.getY(),reader.getZ()}));
                c.getSource().sendSuccess(()->Component.literal("QUEST_FIXTURE "+source),false);return 1;
            }))))
            .then(Commands.literal("approach").then(Commands.argument("player",EntityArgument.player()).then(Commands.argument("group",StringArgumentType.word()).executes(c->{
                var p=EntityArgument.getPlayer(c,"player");var run=QuestSaves.get(p.getServer()).state.instances.get(QuestSaves.get(p.getServer()).state.members.get(p.getUUID()).instance);
                var map=QuestMap.get(run.map);var group=map.groups().stream().filter(g->g.id().equals(StringArgumentType.getString(c,"group"))).findFirst().orElseThrow();var center=group.actors().getFirst().pos();
                for(int r=12;r<=16;r++)for(int[] direction:new int[][]{{0,-1},{-1,0},{1,0},{0,1}})for(int dy=0;dy<=2;dy++){
                    double x=map.origin()[0]+center[0]+direction[0]*r+.5,y=map.origin()[1]+center[1]+dy,z=map.origin()[2]+center[2]+direction[1]*r+.5;
                    var body=new net.minecraft.world.phys.AABB(x-.3,y,z-.3,x+.3,y+1.8,z+.3);var level=p.serverLevel();
                    if(level.noCollision(body)&&!level.noCollision(body.move(0,-.08,0))&&!level.containsAnyLiquid(body)){p.teleportTo(level,x,y,z,java.util.Set.of(),0,0);return 1;}
                }throw new IllegalStateException("没有安全接战夹具站位");
            }))))
            .then(Commands.literal("return_board").then(Commands.argument("player",EntityArgument.player()).executes(c->{
                var p=EntityArgument.getPlayer(c,"player");var state=QuestSaves.get(p.getServer()).state;var member=state.members.get(p.getUUID());
                if(member==null||member.outside==null)return 0;var at=member.outside;var level=p.getServer().getLevel(ResourceKey.create(Registries.DIMENSION,ResourceLocation.parse(at.dimension)));
                p.teleportTo(level,at.x,at.y,at.z,java.util.Set.of(),at.yaw,at.pitch);return 1;
            }))));
    }
    private static BlockPos position(CapitalCity city,JsonObject row,String field){var a=row.getAsJsonArray(field);return new BlockPos(city.x()+a.get(0).getAsInt(),city.originY()+a.get(1).getAsInt(),city.z()+a.get(2).getAsInt());}
}
