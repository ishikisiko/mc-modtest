package com.example.myvillage.entity.npc;

import com.example.myvillage.cultivation.fate.*;
import com.example.myvillage.entity.ModEntities;
import com.example.myvillage.sim.runtime.*;
import com.example.myvillage.stats.*;
import com.example.myvillage.stats.runtime.StatsRuntime;
import com.example.myvillage.stats.runtime.ModStatsAttributes;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.damagesource.DamageTypes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** 原生实体、实际伤害事件和真实服务器重启；不声称玩家输入或 NPC 战术验收。 */
@EventBusSubscriber(modid="myvillage")
public final class NpcFateEvidence {
    private static final Path OUT=Path.of(System.getenv().getOrDefault("MYVILLAGE_NPC_CULTIVATION_OUT","../reports/npc-fates-native"));
    private static final List<Map<String,Object>> CHECKS=new ArrayList<>();
    private static final Map<String,NpcEntity> ACTORS=new LinkedHashMap<>();
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private static boolean waiting,passedRestart;
    private static int waitTicks;
    private static NpcEntity phoenix;
    private static int ledgerId;
    private static void check(String name,boolean pass,Object data){CHECKS.add(Map.of("name",name,"pass",pass,"data",String.valueOf(data)));}
    private static boolean near(double a,double b){return Math.abs(a-b)<.005;}
    private static void report(String phase)throws Exception{
        Files.createDirectories(OUT);Files.writeString(OUT.resolve(phase+".json"),JSON.toJson(Map.of("phase",phase,
                "pass",CHECKS.stream().allMatch(c->c.get("pass").equals(true)),"checks",CHECKS,
                "failed",CHECKS.stream().filter(c->c.get("pass").equals(false)).toList())));
    }
    @SubscribeEvent public static void commands(RegisterCommandsEvent event){event.getDispatcher().register(
            Commands.literal("myvillage_npc_fates_evidence").requires(s->s.hasPermission(2))
                    .then(Commands.literal("begin").executes(c->{try{begin(c.getSource().getServer());return 1;}catch(Exception e){throw new IllegalStateException(e);}}))
                    .then(Commands.literal("restart").executes(c->{try{restart(c.getSource().getServer());return 1;}catch(Exception e){throw new IllegalStateException(e);}})));}
    private static NpcEntity actor(ServerLevel level,String name,String special)throws Exception{
        var ids=List.of("myvillage:"+special,"myvillage:bright","myvillage:herbal_lore");
        int spent=ids.stream().map(FateService.definitions(level.registryAccess())::get).mapToInt(FateDefinition::cost).sum();
        NpcEntity npc=ModEntities.CULTIVATOR.get().create(level);npc.cultivationInitialized=true;npc.setProdigy(spent==9);
        npc.foundationQuality=spent==9?2:1;
        npc.fates=new com.example.myvillage.sim.fate.NpcFateRoll(ids,9,spent);npc.setNoAi(true);npc.spiritualPower=0;
        npc.moveTo(8+ACTORS.size()*3,-60,8,0,0);StatsRuntime.applyRealm(npc,"myvillage:foundation_establishment",0);
        if(!level.addFreshEntity(npc))throw new IllegalStateException("fixture actor rejected");
        npc.setHealth(npc.getMaxHealth());ACTORS.put(name,npc);return npc;
    }
    private static Cow target(ServerLevel level){var cow=EntityType.COW.create(level);cow.moveTo(50,-60,8,0,0);
        cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);level.addFreshEntity(cow);cow.setNoAi(true);cow.setHealth(1000);return cow;}
    private static double hit(NpcEntity npc,Cow cow,float amount){cow.invulnerableTime=0;float before=cow.getHealth();cow.hurt(npc.damageSources().mobAttack(npc),amount);return before-cow.getHealth();}
    private static void begin(MinecraftServer server)throws Exception{
        var level=server.overworld();for(int x=0;x<=4;x++)for(int z=0;z<=1;z++){level.setChunkForced(x,z,true);level.getChunk(x,z);}
        WorldSimRuntime.setPaused(true);var sim=WorldSimRuntime.sim().orElseThrow();
        var robust=actor(level,"robust","robust");var foot=actor(level,"lightfoot","lightfoot");
        var base=CultivationStatsRules.bundled().at(new RealmKey("myvillage:foundation_establishment",0),1,0);
        check("npc_health_effect",near(robust.getMaxHealth(),base.health()*1.15),StatsRuntime.info(robust));
        check("npc_speed_effect",near(foot.getAttributeValue(Attributes.MOVEMENT_SPEED),NpcCombatRules.get().speed()*1.1),StatsRuntime.info(foot));
        var burning=actor(level,"burning","burning_star");var normal=actor(level,"normal","school_insight");
        var cow=target(level);double plain=hit(normal,cow,10),enhanced=hit(burning,cow,10);
        check("npc_damage_bonus",near(enhanced,plain*1.3),List.of(plain,enhanced));
        var wood=actor(level,"wood","wood_spirit_body");wood.setHealth(wood.getMaxHealth()/2);double before=wood.getHealth(),loss=hit(wood,cow,10);
        check("npc_lifesteal",near(wood.getHealth()-before,loss*.08),List.of(before,wood.getHealth(),loss));
        var power=actor(level,"power","open_meridians");check("npc_power_capacity",NpcCultivationRuntime.cap(power)==(long)(base.power()*1.4),NpcCultivationRuntime.cap(power));
        check("capacity_does_not_refill",NpcCultivationRuntime.current(power)==0,NpcCultivationRuntime.current(power));
        var metal=actor(level,"metal","metal_spirit_body");check("npc_body_health_defense",
                near(metal.getMaxHealth(),base.health()*1.3)&&near(StatsRuntime.value(metal,ModStatsAttributes.BODY_DEFENSE),base.defense()*1.3),StatsRuntime.info(metal));
        var lasting=actor(level,"everlasting","everlasting_embryo");
        double beforeHealth=lasting.getMaxHealth(),beforeDefense=StatsRuntime.value(lasting,ModStatsAttributes.BODY_DEFENSE),beforeAttack=lasting.getAttributeValue(Attributes.ATTACK_DAMAGE);
        var credit=new EverlastingState.Counter(2,0);var extras=NpcFateRuntime.extras(lasting);
        extras.add("everlasting",EverlastingState.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE,
                new EverlastingState(credit,credit,credit,1)).getOrThrow());NpcFateRuntime.extras(lasting,extras);
        EverlastingRuntime.tickNpc(lasting);FateRuntime.refresh(lasting);
        check("npc_everlasting_shared_credits",near(lasting.getMaxHealth(),beforeHealth+1)
                &&near(StatsRuntime.value(lasting,ModStatsAttributes.BODY_DEFENSE),beforeDefense+1)
                &&near(lasting.getAttributeValue(Attributes.ATTACK_DAMAGE),beforeAttack+1),EverlastingRuntime.state(lasting));
        var enemy=EntityType.ZOMBIE.create(level);enemy.moveTo(52,-60,8,0,0);enemy.setNoAi(true);level.addFreshEntity(enemy);
        metal.hurt(level.damageSources().mobAttack(enemy),(float)(metal.getMaxHealth()*.4));
        check("npc_jingang_adaptation",JingangRuntime.state(metal).total()>0,JingangRuntime.state(metal));
        var bones=actor(level,"bones","tempered_bones");
        double damagedFrom=bones.getHealth();for(int i=0;i<4;i++){bones.invulnerableTime=0;bones.hurt(level.damageSources().mobAttack(enemy),(float)(bones.getMaxHealth()*.5));}
        check("npc_bones_actual_loss_reaches_threshold",damagedFrom-bones.getHealth()>=bones.getMaxHealth()*FateService.effect(bones,"bones_threshold"),List.of(damagedFrom,bones.getHealth()));
        check("npc_bones_charge",FateService.state(bones).temperedBones().ready(),FateService.state(bones));double retaliated=hit(bones,cow,10);
        check("npc_bones_bonus_damage",near(retaliated,10+bones.getAttributeValue(Attributes.ATTACK_DAMAGE)),retaliated);
        check("npc_bones_consume",!FateService.state(bones).temperedBones().ready(),FateService.state(bones));
        var fire=actor(level,"fire","fire_spirit_body");for(int i=0;i<3;i++)hit(fire,cow,10);
        check("npc_fire_burst_cooldown",FateService.state(fire).fireSpirit().readyAt(FateActors.identity(cow))>FateActors.now(fire),FateService.state(fire));
        var pill=actor(level,"pill","pill_aegis");PillAegisRuntime.afterSuccessfulConsumption(pill,"yangyuan");
        double shield=FateService.state(pill).pillAegis().shield();check("npc_shared_pill_shield_entry",shield>0,FateService.state(pill));
        pill.hurt(level.damageSources().mobAttack(cow),5);check("npc_shield_absorbs_actual_damage",FateService.state(pill).pillAegis().shield()<shield,FateService.state(pill));
        var healing=actor(level,"recovery","alchemist_descendant");healing.setHealth(healing.getMaxHealth()*.25f);healing.hurt(level.damageSources().mobAttack(cow),1);
        check("npc_recovery_saved_timer",FateService.state(healing).healingTicks()>0,FateService.state(healing));
        var id=sim.findPersons("",Integer.MAX_VALUE).stream().filter(p->p.alive()&&sim.life(p.id()).isEmpty()).findFirst().orElseThrow();ledgerId=id.id();
        var projected=ModEntities.CULTIVATOR.get().create(level);projected.becomeLedgerAvatar(ledgerId);StatsRuntime.applyNpcRealm(projected,id);
        check("ledger_projects_birth_fates",projected.innateFates().equals(sim.npcFates(ledgerId).orElseThrow()),projected.innateFates());
        long epoch=sim.claimCombat(ledgerId,new com.example.myvillage.sim.combat.NpcCombatProfile(20,20,"",0,0,"",0,null));
        var runtime=FateState.empty().choice(idList(sim.npcFates(ledgerId).orElseThrow()),"",idList(sim.npcFates(ledgerId).orElseThrow()),true,1).timers(FateActors.now(robust)+1000,0,0,FateActors.now(robust)+2000);
        String encoded=FateState.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE,runtime).getOrThrow().toString();
        check("ledger_runtime_commit",sim.replaceNpcFateRuntime(ledgerId,epoch,"",encoded),encoded);
        check("stale_body_cannot_write",!sim.replaceNpcFateRuntime(ledgerId,epoch+1,encoded,encoded),epoch);
        sim.releaseCombat(ledgerId,epoch);WorldSimSavedData.get(level).setDirty();
        phoenix=actor(level,"phoenix","phoenix_body");phoenix.hurt(level.damageSources().mobAttack(cow),1000000);
        check("npc_phoenix_cancels_death",NpcPhoenixRuntime.active(phoenix)&&phoenix.isAlive()&&FateService.state(phoenix).phoenix().reserve()==0,StatsRuntime.info(phoenix));
        var beforeMotion=phoenix.getDeltaMovement();phoenix.knockback(1,1,0);
        check("npc_phoenix_ember_knockback_immunity",phoenix.getDeltaMovement().equals(beforeMotion),phoenix.getDeltaMovement());
        var snapshot=new JsonObject();snapshot.addProperty("ledger_id",ledgerId);snapshot.addProperty("ledger_runtime",encoded);var actors=new JsonObject();
        for(var entry:ACTORS.entrySet()){var data=new JsonObject();data.addProperty("uuid",entry.getValue().getUUID().toString());data.add("roll",entry.getValue().innateFates().toJson());data.addProperty("runtime",entry.getValue().fateRuntime);actors.add(entry.getKey(),data);}
        snapshot.add("actors",actors);Files.createDirectories(OUT);Files.writeString(OUT.resolve("before-restart.json"),JSON.toJson(snapshot));report("initial");
    }
    private static List<String> idList(com.example.myvillage.sim.fate.NpcFateRoll roll){return roll.selected();}
    private static void restart(MinecraftServer server)throws Exception{
        var before=JsonParser.parseString(Files.readString(OUT.resolve("before-restart.json"))).getAsJsonObject();
        var sim=WorldSimRuntime.sim().orElseThrow();ledgerId=before.get("ledger_id").getAsInt();
        check("restart_ledger_cooldowns",sim.npcFateRuntime(ledgerId).orElseThrow().equals(before.get("ledger_runtime").getAsString()),sim.npcFateRuntime(ledgerId));
        for(var entry:before.getAsJsonObject("actors").entrySet()){
            var saved=entry.getValue().getAsJsonObject();var entity=server.overworld().getEntity(UUID.fromString(saved.get("uuid").getAsString()));
            check(entry.getKey()+"_real_entity_loaded",entity instanceof NpcEntity,entity);
            if(entity instanceof NpcEntity npc){
                check(entry.getKey()+"_birth_roll_retained",npc.innateFates().toJson().equals(saved.get("roll")),npc.innateFates());
                if(!entry.getKey().equals("phoenix"))check(entry.getKey()+"_runtime_retained",npc.fateRuntime.equals(saved.get("runtime").getAsString()),npc.fateRuntime);
                else {phoenix=npc;check("phoenix_restart_keeps_spent_reserve",NpcPhoenixRuntime.active(npc)&&FateService.state(npc).phoenix().reserve()==0,npc.fateRuntime);}
            }
        }
        waiting=true;passedRestart=true;waitTicks=0;
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event){if(!waiting)return;
        try{if(phoenix==null||!phoenix.isAlive()||!NpcPhoenixRuntime.active(phoenix)||++waitTicks>SpiritBodyRules.get().phoenix().emberTicks()+40){
            check("phoenix_reforms_after_restart",phoenix!=null&&phoenix.isAlive()&&!NpcPhoenixRuntime.active(phoenix),phoenix==null?"missing":phoenix.fateRuntime);
            check("phoenix_does_not_refill_reserve",phoenix!=null&&FateService.state(phoenix).phoenix().reserve()==0,phoenix==null?"missing":FateService.state(phoenix));
            waiting=false;report("restart");}}
        catch(Exception failure){waiting=false;throw new IllegalStateException(failure);}}
}
