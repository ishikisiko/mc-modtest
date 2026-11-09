package com.example.myvillage.item;

import com.example.myvillage.aptitude.AptitudeRuntime;
import com.example.myvillage.combat.runtime.CombatActors;
import com.example.myvillage.combat.session.CombatSessionManager;
import com.example.myvillage.cultivation.fate.FateService;
import com.example.myvillage.entity.npc.NpcCultivationRuntime;
import com.example.myvillage.entity.npc.NpcEntity;
import com.example.myvillage.network.TestUnitPayload;
import com.example.myvillage.sim.runtime.WorldSimRuntime;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** 独立平坦世界的开发者探针，编译开关禁止其进入正式 JAR。 */
@EventBusSubscriber(modid="myvillage")
public final class TestUnitServerEvidence {
    private static int ticks, stage, waited;
    private static NpcEntity saved, a, b;
    private static Mob beast;
    private static Mob foreign;
    private static float ah,bh;
    private static boolean aTarget,bTarget,aAction,bAction;
    private static List<String> fates;
    private static final List<String> passed=new ArrayList<>();
    private static int people;
    private static Path out;
    private static void check(boolean value,String description) { if(!value)throw new IllegalStateException(description); }
    private static void expectFailure(Runnable action) { try{action.run();}catch(IllegalArgumentException expected){return;}throw new IllegalStateException("错误配置被接受"); }
    private static TestUnitConfig config(ServerPlayer player,String type,String name,int team,int enemies,boolean monsters,boolean hostilePlayer,boolean pause) {
        boolean npc=type.equals("myvillage:cultivator");
        return new TestUnitConfig(type,name,"myvillage:qi_refining",0,4,3,npc?fates:List.of(),
                npc?Map.of("fire",1234,"sword",567):Map.of(),Map.of("minecraft:generic.max_health",400.0,
                "minecraft:generic.attack_damage",8.0,"myvillage:body_defense",0.0),npc?3456:-1,npc?9000:-1,pause,
                new TestUnitRelations.Unit(player.getUUID(),team,enemies,hostilePlayer,monsters,Map.of()));
    }
    private static JsonObject unit(Mob mob) {var data=new JsonObject();data.addProperty("unit",mob.getUUID().toString());return data;}
    private static void request(ServerPlayer p,String action,JsonObject data){TestUnitRuntime.request(p,new TestUnitPayload(action,data.toString()));}
    private static void save(String file) throws Exception {
        var data=new JsonObject();data.addProperty("pass",true);data.add("completed",new Gson().toJsonTree(passed));
        if(saved!=null){data.addProperty("saved_id",saved.getUUID().toString());data.addProperty("saved_health",saved.getHealth());}
        Files.writeString(out.resolve(file),new GsonBuilder().setPrettyPrinting().create().toJson(data));
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) throws Exception {
        String target=System.getenv("MYVILLAGE_TEST_UNIT_OUT");if(target==null||stage==99||System.getenv("MYVILLAGE_TEST_UNIT_UI_ONLY")!=null)return;
        out=Path.of(target);Files.createDirectories(out);var server=event.getServer();var level=server.overworld();
        try {
            if(System.getenv("MYVILLAGE_TEST_UNIT_RESTART")!=null){
                if(++ticks<40)return;level.setChunkForced(1,0,true);level.getChunk(1,0);
                if(ticks<80)return;
                var old=JsonParser.parseString(Files.readString(out.resolve("server-result.json"))).getAsJsonObject();
                var entity=level.getEntity(UUID.fromString(old.get("saved_id").getAsString()));
                check(entity instanceof NpcEntity,"重启后测试修士不存在");var npc=(NpcEntity)entity;
                check(!npc.isLedgerAvatar()&&npc.foundationQuality()==4&&npc.goldenCoreQuality()==3,"重启后身份或品质变化");
                check(NpcCultivationRuntime.cap(npc)==9000&&NpcCultivationRuntime.current(npc)==3456,"重启后灵力变化");
                check(AptitudeRuntime.score(npc,"fire")==1234&&AptitudeRuntime.score(npc,"sword")==567,"重启后资质变化");
                check(TestUnitRuntime.relations(npc)!=null&&npc.isNoAi(),"重启后关系或暂停状态丢失");
                check(Math.abs(npc.getHealth()-old.get("saved_health").getAsFloat())<0.001,"重启补满或丢失生命");
                check(Math.abs(npc.getAttribute(Attributes.ATTACK_DAMAGE).getModifier(net.minecraft.resources.ResourceLocation.parse("myvillage:test_override")).amount())>0,"重启属性覆盖丢失");
                passed.add("真实世界保存与重启保留普通修士配置和生命");save("restart-result.json");stage=99;
                server.halt(false);return;
            }
            var player=server.getPlayerList().getPlayers().stream().filter(p->p.getGameProfile().getName().equals("TestUnitDev")).findFirst().orElse(null);
            if(player==null)return;
            if(stage==0){
                if(++ticks<60)return;
                player.setGameMode(GameType.CREATIVE);player.teleportTo(level,8,-60,8,Set.of(),-90,45);
                level.setChunkForced(0,0,true);level.setChunkForced(1,0,true);level.getChunk(0,0);level.getChunk(1,0);
                WorldSimRuntime.setPaused(true);people=WorldSimRuntime.sim().orElseThrow().findPersons("",Integer.MAX_VALUE).size();
                fates=FateService.definitions(level.registryAccess()).entrySet().stream().filter(e->e.getValue().enabled())
                        .filter(e->e.getValue().effects().keySet().stream().noneMatch(k->k.equals("phoenix")||k.equals("everlasting")||k.contains("health")||k.contains("recovery")||k.contains("speed")))
                        .map(Map.Entry::getKey).limit(3).toList();check(fates.size()==3,"探针词条不足");
                player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,ItemStack.EMPTY);
                expectFailure(()->TestUnitRuntime.spawn(player,config(player,"myvillage:cultivator","无道具",0,0,false,false,true)));
                player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,new ItemStack(ModItems.QUALITY_PICKER.get()));
                stage=1;return;
            }
            if(stage==1){
                if(++waited<40)return;waited=0;
                var c=config(player,"myvillage:cultivator","保存检查修士",0,0,false,false,true);
                c=new TestUnitConfig(c.entity(),c.name(),"myvillage:golden_core",1,4,3,c.fates(),c.aptitudes(),c.attributes(),3456,9000,true,c.relations());
                saved=(NpcEntity)TestUnitRuntime.spawn(player,c);saved.moveTo(26,-60,8,0,0);saved.setHealth(211);
                check(!saved.isLedgerAvatar()&&saved.shouldBeSaved(),"测试修士被登记成化身");
                check(saved.foundationQuality()==4&&saved.goldenCoreQuality()==3,"品质配置无效");
                check(saved.innateFates().selected().equals(fates),"词条被重抽");
                check(AptitudeRuntime.score(saved,"fire")==1234&&NpcCultivationRuntime.current(saved)==3456&&NpcCultivationRuntime.cap(saved)==9000,"属性配置未应用");
                var nbt=new CompoundTag();saved.saveWithoutId(nbt);var copy=(NpcEntity)saved.getType().create(level);copy.load(nbt);
                check(copy.innateFates().equals(saved.innateFates())&&copy.foundationQuality()==4&&NpcCultivationRuntime.current(copy)==3456,"实体 NBT 往返变化");
                check(TestUnitRuntime.relations(copy).equals(TestUnitRuntime.relations(saved)),"关系往返变化");
                check(!CombatActors.mayHarm(saved,player),"中立单位可以伤害玩家");
                passed.add("手持权限、指定境界品质天赋资质灵力、实体 NBT 和中立保护");
                a=(NpcEntity)TestUnitRuntime.spawn(player,config(player,"myvillage:cultivator","甲方测试修士",0,2,false,false,false));a.moveTo(12,-60,8,0,0);
                b=(NpcEntity)TestUnitRuntime.spawn(player,config(player,"myvillage:cultivator","乙方测试修士",1,0,false,false,false));b.moveTo(15,-60,8,0,0);
                ah=a.getHealth();bh=b.getHealth();stage=2;return;
            }
            if(stage==2){
                aTarget|=a.getTarget()==b;bTarget|=b.getTarget()==a;aAction|=CombatSessionManager.hasAction(a);bAction|=CombatSessionManager.hasAction(b);
                if(++waited<160)return;waited=0;
                check(aTarget&&bTarget&&aAction&&bAction&&a.getHealth()<ah&&b.getHealth()<bh,
                        "NPC 互斗缺少选敌、正式招式或双向实际伤害："+aTarget+","+bTarget+","+aAction+","+bAction+","+a.getHealth()+","+b.getHealth());
                a.setNoAi(true);b.setNoAi(true);com.example.myvillage.entity.npc.NpcCombatRuntime.disengage(a);com.example.myvillage.entity.npc.NpcCombatRuntime.disengage(b);
                passed.add("NPC 互相主动选敌并通过正式招式造成双向伤害");
                var data=unit(a);data.addProperty("other",b.getUUID().toString());data.addProperty("mode",0);request(player,"pair",data);stage=3;return;
            }
            if(stage==3){
                check(!CombatActors.mayHarm(a,b)&&!CombatActors.mayHarm(b,a),"指定中立未覆盖阵营");
                var before=b.getHealth();check(!b.hurt(a.damageSources().mobAttack(a),10)&&b.getHealth()==before,"友好伤害未拦截");
                passed.add("指定中立双向覆盖及伤害事件保护");
                a.discard();b.discard();
                a=(NpcEntity)TestUnitRuntime.spawn(player,config(player,"myvillage:cultivator","打怪测试修士",0,0,true,false,false));a.moveTo(12,-60,8,0,0);
                beast=TestUnitRuntime.spawn(player,config(player,"myvillage:demon_wolf","打怪测试妖狼",0,0,false,false,false));beast.moveTo(15,-60,8,0,0);
                ah=a.getHealth();bh=beast.getHealth();aTarget=bTarget=aAction=false;stage=4;return;
            }
            if(stage==4){
                aTarget|=a.getTarget()==beast;bTarget|=beast.getTarget()==a;aAction|=CombatSessionManager.hasAction(a);
                if(++waited<180)return;waited=0;
                check(aTarget&&bTarget&&aAction&&a.getHealth()<ah&&beast.getHealth()<bh,"NPC 打怪缺少选敌或实际伤害");
                passed.add("NPC 与妖狼主动互相选敌并造成双向伤害");
                a.discard();beast.discard();player.setGameMode(GameType.SURVIVAL);
                a=(NpcEntity)TestUnitRuntime.spawn(player,config(player,"myvillage:cultivator","玩家对战修士",0,0,false,true,false));a.moveTo(12,-60,8,0,0);stage=5;return;
            }
            if(stage==5){
                if(a.getTarget()!=player&&++waited<100)return;
                check(a.getTarget()==player&&CombatActors.mayHarm(a,player)&&CombatActors.mayHarm(player,a),"对玩家敌对未生效");
                player.setGameMode(GameType.CREATIVE);check(!CombatActors.mayHarm(a,player),"测试单位绕过创造无敌");a.discard();
                passed.add("主动选择召唤玩家并保留创造模式无敌");
                foreign=EntityType.ZOMBIE.create(level);foreign.moveTo(18,-60,12,0,0);foreign.setNoAi(true);
                TestUnitRuntime.relations(foreign,new TestUnitRelations.Unit(UUID.randomUUID(),0,15,true,true,Map.of()));level.addFreshEntity(foreign);
                request(player,"remove",unit(foreign));stage=6;return;
            }
            if(stage==6){
                check(!foreign.isRemoved(),"可以删除其他玩家的测试单位");
                player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,ItemStack.EMPTY);
                request(player,"remove",unit(saved));stage=7;return;
            }
            if(stage==7){
                check(!saved.isRemoved(),"未持道具仍能删除测试单位");foreign.discard();
                player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,new ItemStack(ModItems.QUALITY_PICKER.get()));
                passed.add("服务器拒绝删除他人单位及未持道具的修改请求");
                check(WorldSimRuntime.sim().orElseThrow().findPersons("",Integer.MAX_VALUE).size()==people,"测试单位改变命簿人物数");
                passed.add("全程不增加命簿人物");
                saved.setHealth(211);save("server-result.json");stage=99;
            }
        }catch(Throwable failure){Files.writeString(out.resolve("server-failure.txt"),failure.toString());stage=99;throw failure;}
    }
}
