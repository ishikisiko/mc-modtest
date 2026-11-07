package com.example.myvillage.cultivation.fate;

import com.example.myvillage.cultivation.*;
import com.example.myvillage.stats.runtime.StatsRuntime;
import com.google.gson.Gson;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.List;
import java.util.Map;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** 真实客户端采集的管理员夹具；不赋予普通客户端资源、伤害或复生权限。 */
@EventBusSubscriber(modid="myvillage")
public final class SpiritBodyFixtureCommands {
    @SubscribeEvent public static void commands(RegisterCommandsEvent event) {
        var root=Commands.literal("myvillage_spirit_fixture").requires(s->s.hasPermission(2));
        for(String body:List.of("metal_spirit_body","phoenix_body"))root.then(Commands.literal(body)
                .then(Commands.argument("player",EntityArgument.player()).executes(c->prepare(c.getSource(),EntityArgument.getPlayer(c,"player"),body))));
        root.then(Commands.literal("peek").then(Commands.argument("name",StringArgumentType.word()).executes(c->{
            String name=StringArgumentType.getString(c,"name");var player=c.getSource().getServer().getPlayerList().getPlayerByName(name);
            if(player==null)for(var ember:PhoenixEmbersData.get(c.getSource().getServer()).embers.values())if(ember.name.equals(name))player=ember.player;
            if(player==null)return 0;return reply(c.getSource(),player);
        })));
        for(String action:List.of("lethal","hit","fill"))root.then(Commands.literal(action)
                .then(Commands.argument("name",StringArgumentType.word()).executes(c->{
                    String name=StringArgumentType.getString(c,"name");var player=c.getSource().getServer().getPlayerList().getPlayerByName(name);
                    if(player==null)for(var ember:PhoenixEmbersData.get(c.getSource().getServer()).embers.values())if(ember.name.equals(name))player=ember.player;
                    if(player==null)return 0;
                    if(action.equals("fill")){var state=FateService.state(player);CultivationService.replaceFates(player,state.withPhoenix(new PhoenixState(true,SpiritBodyRules.get().phoenix().capacity())),false);player.setHealth(player.getMaxHealth());}
                    else {
                        Zombie enemy=EntityType.ZOMBIE.create(player.serverLevel());enemy.setNoAi(true);enemy.moveTo(player.position().add(2,0,0));player.serverLevel().addFreshEntity(enemy);enemy.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(5);
                        ((com.example.myvillage.mixin.PhoenixServerPlayerAccess)player).myvillage$setEmberSpawnProtection(0);player.invulnerableTime=0;
                        player.hurt(player.damageSources().mobAttack(enemy),action.equals("lethal")?10000:2);
                    }
                    return reply(c.getSource(),player);
                })));
        event.getDispatcher().register(root);
    }
    private static int prepare(CommandSourceStack source,ServerPlayer player,String body) {
        if(PhoenixRuntime.active(player))return 0;
        var profile=CultivationProfile.defaultProfile().withSpiritualRoot(new SpiritualRoot(Map.of(ResourceLocation.parse("myvillage:fire"),10000)))
                .withRealmAndStage(ResourceLocation.parse("myvillage:qi_refining"),ResourceLocation.parse("myvillage:qi_refining_1"))
                .learnTechnique(ResourceLocation.parse("myvillage:basic_breathing"));
        var result=CultivationService.replaceProfile(player,profile);if(!result.success())throw new IllegalStateException(result.message());
        var ids=List.of("myvillage:"+body,"myvillage:bright","myvillage:lightfoot");
        CultivationService.replaceFates(player,new FateState(1,1,ids,"",ids,true,0,0,0,0),true);StatsRuntime.refreshPlayer(player);
        if(body.equals("phoenix_body"))CultivationService.replaceFates(player,FateService.state(player).withPhoenix(new PhoenixState(true,100)),false);
        player.removeAllEffects();player.setHealth(player.getMaxHealth());SpiritBodyRuntime.sync(player);return reply(source,player);
    }
    private static int reply(CommandSourceStack source,ServerPlayer player) {
        var row=Map.of("name",player.getGameProfile().getName(),"uuid",player.getUUID().toString(),"position",List.of(player.getX(),player.getY(),player.getZ()),
                "health",player.getHealth(),"maximum",player.getMaxHealth(),"online",source.getServer().getPlayerList().getPlayer(player.getUUID())==player,
                "state",SpiritBodyRuntime.status(player),"selected",FateService.state(player).selected(),"main_hand",player.getMainHandItem().toString());
        source.sendSuccess(()->Component.literal("SPIRIT_FIXTURE "+new Gson().toJson(row)),false);return 1;
    }
}
