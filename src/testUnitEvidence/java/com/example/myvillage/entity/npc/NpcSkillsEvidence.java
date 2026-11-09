package com.example.myvillage.entity.npc;

import com.example.myvillage.aptitude.AptitudeRules;
import com.example.myvillage.combat.art.ArtDefinition;
import com.example.myvillage.combat.definition.CombatStyles;
import com.example.myvillage.combat.foundation.ControlService;
import com.example.myvillage.combat.foundation.FieldRuntime;
import com.example.myvillage.combat.runtime.*;
import com.example.myvillage.combat.session.*;
import com.example.myvillage.cultivation.data.*;
import com.example.myvillage.cultivation.fate.FateService;
import com.example.myvillage.cultivation.fate.ability.FateAbilityDefinition;
import com.example.myvillage.item.*;
import com.example.myvillage.network.TestUnitPayload;
import com.example.myvillage.sim.runtime.WorldSimRuntime;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** 独立世界服务器行为检查；不计作玩家映射输入。 */
@EventBusSubscriber(modid="myvillage")
public final class NpcSkillsEvidence {
    private record Case(ResourceLocation technique,ArtDefinition art,String family,boolean wave) { }
    private static final List<Case> cases=new ArrayList<>();
    private static final List<JsonObject> receipts=new ArrayList<>();
    private static final List<String> completed=new ArrayList<>();
    private static NpcEntity actor,target,saved;
    private static int stage,ticks,index;
    private static boolean mana,action,pausedChecked;
    private static List<String> neutralFates;
    private static Path out;
    private static final ResourceLocation WAVE=ResourceLocation.parse("myvillage:jianqi_jue");
    private static ResourceLocation gated;
    private static NpcSkillLoadout beforeRequest;
    private static void check(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
    private static NpcSkillLoadout loadout(ResourceLocation id,String family,boolean auto,int priority,long mastery){
        String weapon=CombatStyles.bundled().weapons().stream().filter(w->w.family().orElse("").equals(family)).findFirst().orElseThrow().item().toString();
        return new NpcSkillLoadout(weapon,"",List.of(new NpcSkillLoadout.Learned(id.toString(),mastery,auto,priority)));
    }
    private static TestUnitConfig config(ServerPlayer p,String name,NpcSkillLoadout state,int team,int enemies,boolean pause,String realm,int minor,long power,long cap){
        var aptitudes=new LinkedHashMap<String,Integer>();for(var id:AptitudeRules.get().ids())aptitudes.put(id,1000);
        return new TestUnitConfig("myvillage:cultivator",name,realm,minor,4,3,neutralFates,aptitudes,
                Map.of("minecraft:generic.max_health",1000.0,"minecraft:generic.attack_damage",1.0,"myvillage:body_defense",0.0,
                        "minecraft:generic.movement_speed",0.0),power,cap,pause,
                new TestUnitRelations.Unit(p.getUUID(),team,enemies,false,false,Map.of()),state);
    }
    private static NpcEntity spawn(ServerPlayer p,NpcSkillLoadout skills,String name,int team,int enemies,String realm,int minor,long power,long cap){
        var npc=(NpcEntity)TestUnitRuntime.spawn(p,config(p,name,skills,team,enemies,true,realm,minor,power,cap));
        npc.moveTo(team==0?12:15,-60,8,team==0?-90:90,0);return npc;
    }
    private static void pair(ServerPlayer p,NpcSkillLoadout skills,String realm,int minor,long power,long cap){
        clear();target=spawn(p,NpcSkillLoadout.EMPTY,"技能检查目标",1,0,"myvillage:mortal",1,-1,-1);
        actor=spawn(p,skills,"技能检查修士",0,2,realm,minor,power,cap);ticks=0;mana=action=pausedChecked=false;
    }
    private static void clear(){if(actor!=null){actor.discard();actor=null;}if(target!=null){target.discard();target=null;}}
    private static long mastery(NpcEntity npc,ResourceLocation id){return NpcSkillRuntime.get(npc).learned().stream().filter(r->r.id().equals(id.toString())).findFirst().orElseThrow().mastery();}
    private static void release(){actor.setNoAi(false);NpcCombatRuntime.selectTestTarget(actor,target);}
    private static JsonObject unit(NpcEntity npc){var data=new JsonObject();data.addProperty("unit",npc.getUUID().toString());return data;}
    private static void request(ServerPlayer p,String op,JsonObject data){TestUnitRuntime.request(p,new TestUnitPayload(op,data.toString()));}
    private static boolean ward(){try{var f=Class.forName("com.example.myvillage.combat.elemental.ElementalEffects").getDeclaredField("WARDS");f.setAccessible(true);return ((Map<?,?>)f.get(null)).containsKey(actor.getUUID());}catch(Exception e){throw new IllegalStateException(e);}}
    private static void write(String file,JsonObject data)throws Exception{Files.writeString(out.resolve(file),new GsonBuilder().setPrettyPrinting().create().toJson(data));}
    private static void progress(String phase)throws Exception{
        var data=new JsonObject();data.addProperty("phase",phase);data.addProperty("index",index);data.addProperty("total",cases.size());
        data.add("completed",new Gson().toJsonTree(completed));data.add("casts",new Gson().toJsonTree(receipts));write("server-progress.json",data);
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event)throws Exception{
        String dir=System.getenv("MYVILLAGE_NPC_SKILLS_OUT");if(dir==null)return;out=Path.of(dir);Files.createDirectories(out);
        var server=event.getServer();var level=server.overworld();
        try{
            if(System.getenv("MYVILLAGE_NPC_SKILLS_RESTART")!=null){
                if(++ticks<40)return;level.setChunkForced(1,0,true);level.getChunk(1,0);if(ticks<80)return;
                var fixture=JsonParser.parseString(Files.readString(out.resolve("fixture.json"))).getAsJsonObject();
                var entity=level.getEntity(UUID.fromString(fixture.get("saved_id").getAsString()));check(entity instanceof NpcEntity,"重启后技能修士丢失");saved=(NpcEntity)entity;
                check(!saved.isLedgerAvatar()&&saved.isNoAi(),"重启后身份或暂停状态改变");
                var row=NpcSkillRuntime.get(saved).learned().stream().filter(r->r.id().equals(WAVE.toString())).findFirst().orElseThrow();
                check(row.mastery()==1400&&!row.automatic()&&row.priority()==7,"重启后技能编辑结果丢失");
                check(NpcCultivationRuntime.current(saved)==321&&saved.getHealth()==211,"重启后灵力或生命变化");
                check(NpcSkillRuntime.get(saved).core().equals("myvillage:qingmu_changchun_gong"),"重启后运行心法丢失");
                check(saved.getPersistentData().getCompound("myvillage:npc_skill_cooldowns").getLong(WAVE.toString())==fixture.get("deadline").getAsLong(),"重启后技能冷却丢失");
                check(!CombatSessionManager.busy(saved),"重启恢复了已取消的攻击");
                var result=new JsonObject();result.addProperty("pass",true);result.addProperty("saved_loadout",NpcSkillRuntime.get(saved).json());write("restart-result.json",result);server.halt(false);return;
            }
            var p=server.getPlayerList().getPlayers().stream().filter(player->player.getGameProfile().getName().equals("NpcSkillDev")).findFirst().orElse(null);if(p==null)return;
            if(stage==99){
                var row=NpcSkillRuntime.get(saved).learned().stream().filter(r->r.id().equals(WAVE.toString())).findFirst().orElseThrow();
                if(row.mastery()==1400&&!row.automatic()&&row.priority()==7){var result=new JsonObject();result.addProperty("pass",true);write("applied-result.json",result);}return;
            }
            if(stage==0){
                if(++ticks<40)return;p.setGameMode(GameType.CREATIVE);p.teleportTo(level,8,-60,8,Set.of(),-90,10);
                level.setChunkForced(0,0,true);level.setChunkForced(1,0,true);level.getChunk(0,0);level.getChunk(1,0);
                WorldSimRuntime.setPaused(true);p.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(ModItems.QUALITY_PICKER.get()));
                neutralFates=List.of("myvillage:bright","myvillage:school_insight","myvillage:herbal_lore");
                if(System.getenv("MYVILLAGE_NPC_SKILLS_UI_ONLY")!=null){stage=12;ticks=0;return;}
                var registry=level.registryAccess().registryOrThrow(ModCultivationRegistries.TECHNIQUES);
                for(var art:ArtDefinition.all())if(!art.family().equals("legacy")){
                    var id=registry.keySet().stream().filter(key->registry.get(key).effects().flatMap(TechniqueEffects::active).filter(a->a.skill().equals(art.id())).isPresent()).findFirst().orElseThrow();
                    cases.add(new Case(id,art,art.family().equals("spear")?"spear":art.family().equals("fist")?"fist":"sword",false));
                }
                cases.add(new Case(WAVE,null,"sword",true));stage=1;ticks=0;progress("matrix");return;
            }
            if(stage==1){
                if(index>=cases.size()){stage=3;ticks=0;clear();return;}
                var c=cases.get(index);pair(p,loadout(c.technique(),c.family(),true,0,0),"myvillage:golden_core",2,-1,100000);stage=2;return;
            }
            if(stage==2){
                var c=cases.get(index);if(++ticks==10){release();return;}if(ticks<10)return;
                mana|=NpcCultivationRuntime.current(actor)<100000;action|=CombatSessionManager.hasAction(actor)||CombatSessionManager.charging(actor);
                boolean guarded=c.art()!=null&&c.art().elemental().filter(s->s.role().equals("ward")).isPresent();
                boolean hit=guarded?ward():mastery(actor,c.technique())>0;
                if(mana&&action&&hit){
                    if(guarded){float old=actor.getHealth();target.setNoAi(false);actor.invulnerableTime=0;actor.hurt(target.damageSources().mobAttack(target),10);target.setNoAi(true);
                        float lost=old-actor.getHealth();check(lost>=0&&lost<10,"护体未减少实际伤害");}
                    var receipt=new JsonObject();receipt.addProperty("technique",c.technique().toString());receipt.addProperty("mana_spent",100000-NpcCultivationRuntime.current(actor));receipt.addProperty("action",action);receipt.addProperty("effect",guarded?"ward":"primary hit and mastery");
                    receipt.addProperty("remaining_cooldown",c.art()==null?NpcSkillRuntime.cooldown(actor,c.technique()):CombatArtActions.cooldownRemaining(actor,c.art()));
                    receipts.add(receipt);completed.add(c.technique().toString());index++;progress("matrix");stage=1;return;
                }
                check(ticks<260,"技能未实际施放或命中："+c.technique()+" mana="+mana+" action="+action+" target="+actor.getTarget()+" phase="+actor.combatState());return;
            }
            if(stage==3){
                pair(p,loadout(ResourceLocation.parse("myvillage:qinglian_jiange"),"sword",false,0,0),"myvillage:golden_core",2,-1,100000);stage=4;return;
            }
            if(stage==4){
                if(++ticks==10)release();if(ticks<80)return;
                check(NpcCultivationRuntime.current(actor)==100000&&NpcSkillRuntime.cooldown(actor,ResourceLocation.parse("myvillage:qinglian_jiange"))==0,"关闭自动仍释放技能");completed.add("关闭自动施放");
                pair(p,loadout(ResourceLocation.parse("myvillage:qinglian_jiange"),"sword",true,0,0),"myvillage:qi_refining",0,-1,100000);stage=5;return;
            }
            if(stage==5){
                if(++ticks==10)release();if(ticks<80)return;check(NpcCultivationRuntime.current(actor)==100000,"境界不足仍扣费施放");completed.add("境界不足拒绝");
                pair(p,loadout(WAVE,"sword",true,0,0),"myvillage:golden_core",2,0,0);stage=6;return;
            }
            if(stage==6){
                if(++ticks==10)release();if(ticks<80)return;check(!CombatSessionManager.charging(actor)&&NpcSkillRuntime.cooldown(actor,WAVE)==0&&mastery(actor,WAVE)==0&&NpcCultivationRuntime.current(actor)==0,"无灵力仍蓄力或施放");completed.add("灵力不足拒绝");
                var high=ResourceLocation.parse("myvillage:qinglian_jiange");var state=loadout(high,"sword",true,0,0);
                pair(p,new NpcSkillLoadout(state.weapon(),"",List.of(state.learned().getFirst(),new NpcSkillLoadout.Learned(WAVE.toString(),0,true,10))),"myvillage:golden_core",2,-1,100000);stage=7;return;
            }
            if(stage==7){
                if(++ticks==10)release();if(ticks<10)return;
                var high=ResourceLocation.parse("myvillage:qinglian_jiange");
                if(!pausedChecked&&NpcSkillRuntime.cooldown(actor,high)>0){check(NpcSkillRuntime.cooldown(actor,WAVE)==0&&!CombatSessionManager.charging(actor),"优先级未生效");pausedChecked=true;}
                if(pausedChecked&&NpcSkillRuntime.cooldown(actor,WAVE)>0){completed.add("优先级及高优先级冷却时选择后续技能");clear();stage=8;ticks=0;return;}
                check(ticks<260,"没有按优先级施放及冷却换招");return;
            }
            if(stage==8){
                var movement=level.registryAccess().registryOrThrow(ModCultivationRegistries.TECHNIQUES).keySet().stream().filter(id->ModCultivationRegistries.technique(level.registryAccess(),id).filter(d->d.category()==TechniqueCategory.MOVEMENT&&d.effects().flatMap(TechniqueEffects::movement).isPresent()).isPresent()).findFirst().orElseThrow();
                pair(p,loadout(movement,"sword",true,0,0),"myvillage:golden_core",2,-1,100000);stage=9;return;
            }
            if(stage==9){
                if(++ticks==10){target.moveTo(13.7,-60,8,90,0);release();target.setNoAi(false);NpcCombatRuntime.selectTestTarget(target,actor);}
                if(CombatDodgeService.isDodging(actor)){check(NpcSkillRuntime.get(actor).learned().getFirst().mastery()>0,"身法熟练度未增加");completed.add("自动地面身法与正式无敌窗口");clear();stage=10;ticks=0;return;}
                check(ticks<260,"NPC 未在受到近战威胁时使用身法");return;
            }
            if(stage==10){
                var ability=FateAbilityDefinition.all().getFirst();var old=neutralFates;neutralFates=List.of(ability.requiredFate().toString(),"myvillage:bright","myvillage:herbal_lore");
                pair(p,loadout(ability.id(),"sword",true,0,0),"myvillage:golden_core",2,-1,100000);neutralFates=old;stage=11;return;
            }
            if(stage==11){
                if(++ticks==10)release();
                if(ControlService.isRooted(target)&&FieldRuntime.count(actor)>0){completed.add("天赋能力领域、真实根缠及 NPC 来源保留");clear();stage=20;ticks=0;return;}
                check(ticks<260,"NPC 天赋领域没有造成根缠");return;
            }
            if(stage==20){
                var c=cases.stream().filter(value->ModCultivationRegistries.technique(level.registryAccess(),value.technique()).orElseThrow()
                        .requirements().minimumElementAptitude().values().stream().anyMatch(v->v>0)).findFirst().orElseThrow();
                gated=c.technique();pair(p,loadout(gated,c.family(),true,0,0),"myvillage:golden_core",2,-1,100000);
                var data=actor.getData(com.example.myvillage.cultivation.CultivationAttachments.APTITUDES.get());var values=data.scores();
                for(var id:AptitudeRules.get().elements())values=values.with(id,0);
                actor.setData(com.example.myvillage.cultivation.CultivationAttachments.APTITUDES.get(),data.withScores(values));stage=21;return;
            }
            if(stage==21){
                if(++ticks==10)release();if(ticks<80)return;
                check(NpcCultivationRuntime.current(actor)==100000&&mastery(actor,gated)==0,"灵根不足仍施放");completed.add("灵根不足拒绝");
                var state=loadout(ResourceLocation.parse("myvillage:qinglian_jiange"),"spear",true,0,0);
                pair(p,state,"myvillage:golden_core",2,-1,100000);stage=23;return;
            }
            if(stage==23){
                if(++ticks==10)release();if(ticks<80)return;
                check(NpcCultivationRuntime.current(actor)==100000&&NpcSkillRuntime.cooldown(actor,ResourceLocation.parse("myvillage:qinglian_jiange"))==0,"武器不符仍施放");
                completed.add("武器类别不符拒绝");pair(p,loadout(WAVE,"sword",true,0,0),"myvillage:golden_core",2,-1,100000);
                for(int y=-60;y<=-57;y++)for(int z=6;z<=10;z++)level.setBlockAndUpdate(new BlockPos(14,y,z),Blocks.STONE.defaultBlockState());stage=25;return;
            }
            if(stage==25){
                if(++ticks==10)release();if(ticks<80)return;
                check(NpcCultivationRuntime.current(actor)==100000&&mastery(actor,WAVE)==0,"墙后仍自动施放剑气");completed.add("墙体阻挡自动瞄准");
                for(int y=-60;y<=-57;y++)for(int z=6;z<=10;z++)level.setBlockAndUpdate(new BlockPos(14,y,z),Blocks.AIR.defaultBlockState());
                pair(p,loadout(WAVE,"sword",true,0,0),"myvillage:golden_core",2,-1,100000);
                TestUnitRuntime.relations(actor,new TestUnitRelations.Unit(p.getUUID(),0,0,false,false,Map.of()));stage=27;return;
            }
            if(stage==27){
                if(++ticks==10){actor.setNoAi(false);actor.setTarget(target);}if(ticks<80)return;
                check(NpcCultivationRuntime.current(actor)==100000&&mastery(actor,WAVE)==0&&target.getHealth()==target.getMaxHealth(),"中立目标受到技能伤害");
                completed.add("中立目标关系检查");actor.setNoAi(true);beforeRequest=NpcSkillRuntime.get(actor);
                p.setItemSlot(EquipmentSlot.MAINHAND,ItemStack.EMPTY);var data=unit(actor);data.add("skills",JsonParser.parseString(NpcSkillLoadout.EMPTY.json()));request(p,"skills",data);stage=30;ticks=0;return;
            }
            if(stage==30){
                if(++ticks<3)return;check(NpcSkillRuntime.get(actor).equals(beforeRequest),"未持取物器仍能改技能");completed.add("技能请求要求持有取物器");
                p.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(ModItems.QUALITY_PICKER.get()));
                TestUnitRuntime.relations(actor,new TestUnitRelations.Unit(UUID.randomUUID(),0,0,false,false,Map.of()));
                var data=unit(actor);data.add("skills",JsonParser.parseString(NpcSkillLoadout.EMPTY.json()));request(p,"skills",data);stage=32;ticks=0;return;
            }
            if(stage==32){
                if(++ticks<3)return;check(NpcSkillRuntime.get(actor).equals(beforeRequest),"可以修改其他召唤者的 NPC 技能");
                completed.add("技能请求检查召唤归属");clear();stage=12;ticks=0;return;
            }
            if(stage==12){
                saved=spawn(p,loadout(WAVE,"sword",true,0,1200),"可编辑技能修士",0,0,"myvillage:golden_core",1,321,9000);saved.moveTo(26,-60,8,0,0);saved.setHealth(211);
                NpcSkillRuntime.cooldown(saved,WAVE,level.getGameTime()+10000);
                var tag=new CompoundTag();saved.saveWithoutId(tag);var copy=(NpcEntity)saved.getType().create(level);copy.load(tag);
                check(NpcSkillRuntime.get(copy).equals(NpcSkillRuntime.get(saved)),"实体 NBT 没有保存技能配置");
                completed.add("技能和熟练度实体 NBT 往返");
                var fixture=new JsonObject();fixture.addProperty("saved_id",saved.getUUID().toString());fixture.addProperty("deadline",saved.getPersistentData().getCompound("myvillage:npc_skill_cooldowns").getLong(WAVE.toString()));write("fixture.json",fixture);
                var result=new JsonObject();result.addProperty("pass",true);result.add("completed",new Gson().toJsonTree(completed));result.add("casts",new Gson().toJsonTree(receipts));write("server-result.json",result);stage=99;progress("ready_for_ui");
            }
        }catch(Throwable failure){var result=new JsonObject();result.addProperty("failure",failure.toString());result.addProperty("stage",stage);result.addProperty("index",index);write("server-failure.json",result);throw failure;}
    }
}
