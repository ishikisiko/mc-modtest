package com.example.myvillage.entity.npc;

import com.example.myvillage.cultivation.qi.SpiritualPowerRuntime;
import com.example.myvillage.entity.ModEntities;
import com.example.myvillage.sim.PersonView;
import com.example.myvillage.sim.life.*;
import com.example.myvillage.sim.runtime.*;
import com.example.myvillage.stats.*;
import com.example.myvillage.stats.runtime.*;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** 只在专用 fixture 中加载，不进入交付 jar；命令准备与客户端映射输入分别记录。 */
@EventBusSubscriber(modid="myvillage")
public final class NpcCultivationEvidence {
    private static final Path OUT=Path.of(System.getenv().getOrDefault("MYVILLAGE_NPC_CULTIVATION_OUT","../reports/npc-native"));
    private static final List<Map<String,Object>> CHECKS=new ArrayList<>();
    private static final List<String> FAILED=new ArrayList<>();
    private static final Map<String,NpcEntity> ACTORS=new LinkedHashMap<>();
    private static int start=-1,ledgerId=-1;
    private static boolean complete;
    private static CompoundTag reload;
    private static boolean close(double a,double b){return Math.abs(a-b)<.003;}
    private static void check(String name,boolean ok,Object detail){CHECKS.add(Map.of("name",name,"pass",ok,"detail",String.valueOf(detail)));if(!ok)FAILED.add(name+": "+detail);}
    private static void save(String phase)throws Exception{
        Files.createDirectories(OUT);var r=new LinkedHashMap<String,Object>();r.put("phase",phase);r.put("pass",complete&&FAILED.isEmpty());r.put("checks",CHECKS);r.put("failed",FAILED);
        r.put("ledger_id",ledgerId);r.put("actors",ACTORS.entrySet().stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey,e->e.getValue().getUUID().toString())));
        Files.writeString(OUT.resolve(phase+".json"),new GsonBuilder().setPrettyPrinting().create().toJson(r));
    }
    @SubscribeEvent public static void commands(RegisterCommandsEvent e){
        e.getDispatcher().register(Commands.literal("myvillage_npc_evidence").requires(s->s.hasPermission(2))
            .then(Commands.literal("begin").executes(c->run(c.getSource().getServer(),false)))
            .then(Commands.literal("restart").executes(c->run(c.getSource().getServer(),true))));
    }
    private static int run(MinecraftServer server,boolean restart) {
        try { if(restart)restart(server);else begin(server);return 1; }
        catch(Exception ex){FAILED.add(ex.toString());complete=true;try{save(restart?"restart":"initial");}catch(Exception ignored){}throw new IllegalStateException(ex);}
    }
    private static NpcEntity actor(ServerLevel l,String name,boolean prodigy,int fq,int gq,String realm,int stage,double x,double z){
        NpcEntity npc=ModEntities.CULTIVATOR.get().create(l);if(npc==null)throw new IllegalStateException("NPC factory");
        npc.cultivationInitialized=true;npc.setProdigy(prodigy);npc.foundationQuality=fq;npc.goldenCoreQuality=gq;npc.spiritualPower=17;
        npc.setCustomName(Component.literal(name));npc.setCustomNameVisible(true);npc.setLook("default");npc.moveTo(x,-60,z,180,0);
        StatsRuntime.applyRealm(npc,realm,stage);if(!l.addFreshEntity(npc))throw new IllegalStateException("NPC rejected");
        npc.setItemSlot(EquipmentSlot.MAINHAND,ItemStack.EMPTY);npc.setHealth(7);
        var body=CultivationStatsRules.bundled().at(new RealmKey(realm,stage),fq,gq);
        check(name+"_native_attributes",close(npc.getMaxHealth(),body.health())&&close(npc.getAttributeValue(Attributes.ATTACK_DAMAGE),body.attack())
            &&close(StatsRuntime.value(npc,ModStatsAttributes.BODY_DEFENSE),body.defense()),StatsRuntime.info(npc));
        for(int i=0;i<4;i++)StatsRuntime.applyRealm(npc,realm,stage);
        check(name+"_no_refill_or_modifier_stack",npc.getHealth()==7&&NpcCultivationRuntime.current(npc)==17
            &&npc.getAttribute(Attributes.MAX_HEALTH).getModifiers().stream().filter(m->m.id().equals(StatsRuntime.REALM_HEALTH)).count()==1,StatsRuntime.info(npc));
        ACTORS.put(name,npc);return npc;
    }
    private static void begin(MinecraftServer server)throws Exception{
        if(start>=0)throw new IllegalStateException("already begun");
        var l=server.overworld();for(int x=-1;x<=3;x++)for(int z=-1;z<=1;z++){l.setChunkForced(x,z,true);l.getChunk(x,z);}
        WorldSimRuntime.setPaused(true);var sim=WorldSimRuntime.sim().orElseThrow();
        for(var p:sim.findPersons("",Integer.MAX_VALUE))if(p.alive()&&p.status().equals("at_sect")&&sim.life(p.id()).isEmpty()){
            ledgerId=p.id();sim.registerLifeSite(new LifeSite("npc_evidence_ledger","验收院落","minecraft:overworld",p.regionId(),20000,-60,20000,List.of(
                LifePlace.free("home","rest",20000,-60,20000),LifePlace.free("desk","social",20002,-60,20000))));
            sim.assignLife(p.id(),"npc_evidence_ledger","visitor","home","desk");
            if(p.spiritualPower()>7)check("ledger_spend",sim.trySpendSpiritualPower(p.id(),p.spiritualPower()-7),p);
            break;
        }
        check("ledger_person_selected",ledgerId>0,ledgerId);
        // 每个真实命簿人物的品质都投影至实际原生属性，不通过实体 NBT 重新决定身份。
        int projected=0;for(var p:sim.findPersons("",Integer.MAX_VALUE))if(p.alive()){
            NpcEntity probe=ModEntities.CULTIVATOR.get().create(l);probe.becomeLedgerAvatar(p.id());StatsRuntime.applyNpcRealm(probe,p);
            var body=CultivationStatsRules.bundled().at(new RealmKey(p.realmId(),p.stage()),p.foundationQuality(),p.goldenCoreQuality());
            check("ledger_native_"+p.id(),probe.prodigy()==p.prodigy()&&probe.foundationQuality()==p.foundationQuality()&&probe.goldenCoreQuality()==p.goldenCoreQuality()
                &&close(probe.getMaxHealth(),body.health())&&close(probe.getAttributeValue(Attributes.ATTACK_DAMAGE),body.attack())
                &&close(StatsRuntime.value(probe,ModStatsAttributes.BODY_DEFENSE),body.defense()),StatsRuntime.info(probe));projected++;
        }
        check("ledger_projection_sample",projected>20,projected);
        actor(l,"沈清岚",true,2,0,"myvillage:foundation_establishment",0,8.5,11.5);
        actor(l,"林知远",false,1,0,"myvillage:foundation_establishment",0,12.5,11.5);
        actor(l,"筑基上品",true,3,0,"myvillage:foundation_establishment",0,20.5,11.5);
        actor(l,"金丹中品",true,2,2,"myvillage:golden_core",2,24.5,11.5);
        actor(l,"金丹上品",true,3,3,"myvillage:golden_core",2,28.5,11.5);
        actor(l,"元婴初期",false,1,1,"myvillage:nascent_soul",0,32.5,11.5);
        actor(l,"元婴中期",false,1,1,"myvillage:nascent_soul",1,36.5,11.5);
        actor(l,"元婴后期",false,1,1,"myvillage:nascent_soul",2,40.5,11.5);
        for(var e:ACTORS.entrySet()){
            var n=e.getValue();check(e.getKey()+"_atomic_refusal",!SpiritualPowerRuntime.spend(n,18)&&NpcCultivationRuntime.current(n)==17,StatsRuntime.info(n));
            check(e.getKey()+"_shared_spend",SpiritualPowerRuntime.spend(n,7)&&NpcCultivationRuntime.current(n)==10,StatsRuntime.info(n));
        }
        var target=ACTORS.get("筑基上品");target.setNoAi(true);target.setHealth(target.getMaxHealth());
        var attacker=net.minecraft.world.entity.EntityType.ZOMBIE.create(l);attacker.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(20);
        float hp=target.getHealth();boolean hit=target.hurt(target.damageSources().mobAttack(attacker),20);
        check("npc_defense_applies_once",hit&&close(hp-target.getHealth(),15),StatsRuntime.lastTrace(target));
        target.setTarget(null);target.setNoAi(false);target.setHealth(7);
        start=server.getTickCount();save("running");
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void tick(ServerTickEvent.Post e){
        if(start<0||complete)return;
        try{
            int elapsed=e.getServer().getTickCount()-start;
            var rules=SpiritualPowerRuntime.rules();var main=ACTORS.get("沈清岚");
            if(elapsed==rules.spendDelayTicks()-1)check("same_full_spend_delay",NpcCultivationRuntime.current(main)==10,NpcCultivationRuntime.current(main));
            if(elapsed==rules.spendDelayTicks())check("same_first_recovery",NpcCultivationRuntime.current(main)==11,NpcCultivationRuntime.current(main));
            if(elapsed==rules.spendDelayTicks()+rules.regenIntervalTicks()){
                check("same_next_interval",NpcCultivationRuntime.current(main)==12,NpcCultivationRuntime.current(main));
                reload=new CompoundTag();main.saveWithoutId(reload);main.discard();
            }
            if(elapsed==rules.spendDelayTicks()+rules.regenIntervalTicks()+1){
                var clone=ModEntities.CULTIVATOR.get().create(e.getServer().overworld());clone.load(reload);
                check("ordinary_nbt_reloads_identity_quality_and_qi",clone.prodigy()&&clone.foundationQuality()==2&&NpcCultivationRuntime.current(clone)==12
                    &&close(clone.getMaxHealth(),66),StatsRuntime.info(clone));
                if(!e.getServer().overworld().addFreshEntity(clone))throw new IllegalStateException("reloaded NPC rejected");ACTORS.put("沈清岚",clone);
            }
            if(elapsed==rules.spendDelayTicks()+rules.regenIntervalTicks()+10){
                main=ACTORS.get("沈清岚");check("reload_does_not_shorten_recovery_delay",NpcCultivationRuntime.current(main)==12,NpcCultivationRuntime.current(main));
                var state=new JsonObject();state.addProperty("ledger_id",ledgerId);var person=WorldSimRuntime.sim().orElseThrow().person(ledgerId).orElseThrow();
                state.add("ledger",new Gson().toJsonTree(person));var actors=new JsonObject();
                for(var entry:ACTORS.entrySet()){
                    var n=entry.getValue();n.setNoAi(true);n.getNavigation().stop();n.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                    var tag=new CompoundTag();n.saveWithoutId(tag);NbtIo.writeCompressed(tag,OUT.resolve(entry.getKey()+".nbt"));
                    actors.add(entry.getKey(),new Gson().toJsonTree(Map.of("uuid",n.getUUID().toString(),"qi",NpcCultivationRuntime.current(n),"info",StatsRuntime.info(n))));
                }
                state.add("actors",actors);Files.writeString(OUT.resolve("before-restart.json"),new GsonBuilder().setPrettyPrinting().create().toJson(state));
                WorldSimSavedData.get(e.getServer().overworld()).setDirty();complete=true;save("initial");
            }
        }catch(Exception ex){FAILED.add(ex.toString());complete=true;try{save("initial");}catch(Exception ignored){}ex.printStackTrace();}
    }
    private static void restart(MinecraftServer server)throws Exception{
        var before=JsonParser.parseString(Files.readString(OUT.resolve("before-restart.json"))).getAsJsonObject();ledgerId=before.get("ledger_id").getAsInt();
        var person=WorldSimRuntime.sim().orElseThrow().person(ledgerId).orElseThrow();
        check("server_restart_retains_ledger_identity_quality_qi",before.get("ledger").equals(new Gson().toJsonTree(person)),person);
        for(var entry:before.getAsJsonObject("actors").entrySet()){
            var saved=entry.getValue().getAsJsonObject();var tag=NbtIo.readCompressed(OUT.resolve(entry.getKey()+".nbt"),NbtAccounter.unlimitedHeap());
            var npc=ModEntities.CULTIVATOR.get().create(server.overworld());npc.load(tag);
            check(entry.getKey()+"_restart_entity_state",NpcCultivationRuntime.current(npc)==saved.get("qi").getAsLong()
                &&new Gson().toJsonTree(StatsRuntime.info(npc)).equals(saved.get("info")),StatsRuntime.info(npc));
            ACTORS.put(entry.getKey(),npc);
        }
        complete=true;save("restart");
    }
}
