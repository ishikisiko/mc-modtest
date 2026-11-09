package com.example.myvillage.entity.npc;

import com.example.myvillage.cultivation.fate.FateService;
import com.example.myvillage.entity.ModEntities;
import com.example.myvillage.npc.NpcDialogue;
import com.example.myvillage.sim.runtime.WorldSimRuntime;
import com.example.myvillage.stats.runtime.StatsRuntime;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.nio.file.*;
import java.util.List;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** 原生界面采集准备，排除在交付包外；命簿详情仍经正式查询同步。 */
@EventBusSubscriber(modid="myvillage")
public final class NpcFateUiServerEvidence {
    private static int ticks;
    private static boolean prepared;
    private static NpcEntity pending;
    private static int readyTicks;
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) throws Exception {
        String output=System.getenv("MYVILLAGE_NPC_FATE_UI_OUT");
        if(output==null||prepared)return;
        var server=event.getServer();
        var player=server.getPlayerList().getPlayers().stream().filter(p->p.getGameProfile().getName().equals("FateUiDev")).findFirst().orElse(null);
        if(player==null)return;
        if(pending!=null){
            if(++readyTicks<40)return;
            if(!NpcDialogue.valid(player,pending)){
                if(readyTicks>200)throw new IllegalStateException("NPC 准备超时：managed="+NpcCombatRuntime.managed(pending)
                        +", ticking="+NpcCombatRuntime.canTick(pending)+", distance="+player.distanceToSqr(pending)
                        +", sight="+player.hasLineOfSight(pending));
                return;
            }
            NpcDialogue.open(player,pending);prepared=true;return;
        }
        if(++ticks<60)return;
        var level=server.overworld();
        player.setGameMode(GameType.CREATIVE);
        player.teleportTo(level,8,-60,8,Set.of(),-90,0);
        player.getAbilities().flying=true;player.onUpdateAbilities();
        level.setChunkForced(0,0,true);level.getChunk(0,0);
        var npc=ModEntities.CULTIVATOR.get().create(level);
        var ids=List.of("myvillage:phoenix_body","myvillage:bright","myvillage:robust");
        int cost=ids.stream().map(FateService.definitions(level.registryAccess())::get).mapToInt(d->d.cost()).sum();
        npc.cultivationInitialized=true;npc.setProdigy(cost==9);
        npc.fates=new com.example.myvillage.sim.fate.NpcFateRoll(ids,9,cost);
        npc.setNoAi(true);npc.setCustomName(Component.literal("天赋展示修士"));npc.moveTo(10,-60,8,0,0);
        StatsRuntime.applyRealm(npc,"myvillage:qi_refining",2);level.addFreshEntity(npc);
        var sim=WorldSimRuntime.sim().orElseThrow();WorldSimRuntime.setPaused(true);
        var person=sim.findPersons("",Integer.MAX_VALUE).stream().filter(p->p.alive()).findFirst().orElseThrow();
        var data=new JsonObject();data.addProperty("world_person_id",person.id());
        data.add("world_fates",sim.npcFates(person.id()).orElseThrow().toJson());
        data.addProperty("npc_entity_id",npc.getId());data.add("npc_fates",npc.innateFates().toJson());
        var out=Path.of(output);Files.createDirectories(out);
        Files.writeString(out.resolve("fixture.json"),new GsonBuilder().setPrettyPrinting().create().toJson(data));
        pending=npc;
    }
}
