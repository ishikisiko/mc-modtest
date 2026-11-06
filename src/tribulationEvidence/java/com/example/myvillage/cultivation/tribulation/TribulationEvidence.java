package com.example.myvillage.cultivation.tribulation;

import com.example.myvillage.cultivation.*;
import com.example.myvillage.cultivation.data.ModCultivationRegistries;
import com.example.myvillage.stats.*;
import com.example.myvillage.stats.runtime.*;
import com.example.myvillage.combat.foundation.ShieldRuntime;
import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** 默认真实时序的服务器 fixture；不代表玩家映射输入或自然生存平衡。 */
@EventBusSubscriber(modid="myvillage")
public final class TribulationEvidence {
    private static final boolean ENABLED="1".equals(System.getenv("MYVILLAGE_TRIBULATION_EVIDENCE"));
    private static final boolean RESTART="restart".equals(System.getenv("MYVILLAGE_TRIBULATION_PHASE"));
    private static final Path OUT=Path.of(System.getenv().getOrDefault("MYVILLAGE_TRIBULATION_OUT","../reports/tribulation-native"));
    private static final List<Map<String,Object>> CHECKS=new ArrayList<>();
    private static final List<String> FAILED=new ArrayList<>(),DONE=new ArrayList<>();
    private static final List<String> SELECTED=RESTART?List.of("restart_same_trial_no_replay","finish_persisted_grade_once"):
            List.of("eligibility_and_cancel","heaven","golden_low","golden_middle","golden_high","fully_shielded_hits","low_health_failure","rejected_hit","boundary_failure","save_active_trial");
    private static final List<Trial> TRIALS=new ArrayList<>();
    private static int ticks;private static boolean finished;private static ServerPlayer persisted;
    private static final class Trial {
        final String name;final ServerPlayer p;final TribulationKind kind;final int startingStability;final float health;final boolean shield;
        int lastHits;boolean done;
        Trial(String name,ServerPlayer p,TribulationKind kind,boolean shield){this.name=name;this.p=p;this.kind=kind;this.shield=shield;startingStability=CultivationService.getProfile(p).stability();health=p.getHealth();}
    }
    private static class HurtPlayer extends FakePlayer {
        HurtPlayer(ServerLevel l,String name){super(l,new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)),name));}
        @Override public boolean isInvulnerableTo(net.minecraft.world.damagesource.DamageSource source){return getAbilities().invulnerable;}
    }
    private static boolean close(double a,double b){return Math.abs(a-b)<.003;}
    private static void check(String name,boolean pass,Object detail){CHECKS.add(Map.of("name",name,"pass",pass,"detail",String.valueOf(detail)));if(!pass)FAILED.add(name+": "+detail);}
    private static void drive(ServerPlayer p)throws Exception{
        var method=TribulationManager.class.getDeclaredMethod("tickPlayer",ServerPlayer.class);method.setAccessible(true);method.invoke(null,p);
    }
    private static ServerPlayer player(ServerLevel l,String name,TribulationKind kind)throws Exception{
        var p=new HurtPlayer(l,name);p.setGameMode(GameType.SURVIVAL);
        var grace=ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");grace.setAccessible(true);grace.setInt(p,0);
        p.setPos(8.5,l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,8,8),8.5);p.setOnGround(true);
        var profile=CultivationProfile.DEFAULT.withSpiritualRoot(new SpiritualRoot(Map.of(ResourceLocation.parse("myvillage:fire"),10000)))
                .learnTechnique(ModCultivationRegistries.BASIC_BREATHING_TECHNIQUE_ID)
                .withRealmAndStage(ResourceLocation.parse(kind.sourceRealm()),ResourceLocation.parse(kind.sourceStage())).withFoundationQuality(kind==TribulationKind.HEAVEN?0:1).withCurrentSpiritualPower(17);
        var realm=l.registryAccess().registryOrThrow(ModCultivationRegistries.REALMS).get(profile.realmId());
        var stageId=profile.stageId();
        var stage=realm.stages().stream().filter(s->s.id().equals(stageId)).findFirst().orElseThrow();
        profile=profile.withCultivationProgress(stage.cultivationCap().orElseThrow()).withStability(stage.advancement().orElseThrow().requiredStability());
        var result=CultivationService.replaceProfile(p,profile);if(!result.success())throw new IllegalStateException(result.message());
        p.getAttribute(Attributes.ARMOR).setBaseValue(20);p.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(8);p.setHealth(p.getMaxHealth());return p;
    }
    private static void add(ServerLevel l,String name,TribulationKind kind,boolean shield)throws Exception{
        var p=player(l,"Trial_"+name,kind);var trial=new Trial(name,p,kind,shield);
        if(shield)check("shield_grant",ShieldRuntime.grant(p,ResourceLocation.parse("fixture:trial"),1000,10000),"1000 temporary fixture shield");
        if(name.equals("low_health_failure"))p.setHealth(1);
        check("start_"+name,TribulationManager.start(p,kind),TribulationManager.statusJson(p));
        if(name.equals("rejected_hit"))p.getAbilities().invulnerable=true;
        TRIALS.add(trial);
    }
    private static void setup(MinecraftServer server)throws Exception{
        var l=server.overworld();for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){l.setChunkForced(x,z,true);l.getChunk(x,z);}
        var bad=player(l,"Trial_bad",TribulationKind.HEAVEN);var ready=CultivationService.getProfile(bad);
        CultivationService.replaceProfile(bad,ready.withStability(ready.stability()-1));check("short_stability_refused",!TribulationManager.start(bad,TribulationKind.HEAVEN),TribulationManager.statusJson(bad));
        CultivationService.replaceProfile(bad,ready.withCultivationProgress(ready.cultivationProgress()-1));check("short_progress_refused",!TribulationManager.start(bad,TribulationKind.HEAVEN),CultivationService.getProfile(bad));
        CultivationService.replaceProfile(bad,ready);check("wrong_grade_source_refused",!TribulationManager.start(bad,TribulationKind.GOLDEN_HIGH),CultivationService.getProfile(bad));
        check("initial_prepare_accepted",TribulationManager.start(bad,TribulationKind.HEAVEN),TribulationManager.statusJson(bad));
        check("duplicate_start_refused",!TribulationManager.start(bad,TribulationKind.HEAVEN),TribulationManager.statusJson(bad));
        check("prepare_cancel_no_reward",TribulationManager.cancelPreparation(bad)&&CultivationService.getProfile(bad).equals(ready),TribulationManager.statusJson(bad));DONE.add("eligibility_and_cancel");
        for(var kind:TribulationKind.values())add(l,kind.id(),kind,false);
        add(l,"fully_shielded_hits",TribulationKind.HEAVEN,true);add(l,"low_health_failure",TribulationKind.HEAVEN,false);
        add(l,"rejected_hit",TribulationKind.HEAVEN,false);add(l,"boundary_failure",TribulationKind.HEAVEN,false);
    }
    private static void tickTrials()throws Exception{
        for(var t:TRIALS){
            if(t.done)continue;drive(t.p);var a=TribulationManager.load(t.p);if(a==null)throw new IllegalStateException(t.name+" lost attempt");
            if(a.acceptedHits()!=t.lastHits){
                check(t.name+"_hit_"+a.acceptedHits(),a.acceptedHits()==t.lastHits+1,TribulationManager.statusJson(a));t.lastHits=a.acceptedHits();
                if(t.lastHits==1){
                    var trace=StatsRuntime.lastTrace(t.p).orElseThrow();
                    check(t.name+"_body_and_armor_once",trace.bodyDefense()!=null&&trace.armor()!=null&&close(trace.armor().defenderScale(),1)
                            &&close(trace.armor().damageAtArmor(),trace.bodyDefense().afterDefense()),trace);
                    if(t.shield)check("shielded_hit_counts_without_life_loss",close(t.p.getHealth(),t.health)&&trace.actualHealthLoss()==0,trace);
                    if(t.name.equals("boundary_failure"))t.p.setPos(a.x()+a.rules().arenaRadius()+1,a.y(),a.z());
                }
            }
            if(a.active())continue;t.done=true;var profile=CultivationService.getProfile(t.p);
            if(t.name.equals("low_health_failure")||t.name.equals("boundary_failure")){
                check(t.name+"_no_quality_and_once_penalty",profile.foundationQuality()==0&&profile.stability()==t.startingStability-a.rules().failureStabilityLoss(a.requiredStability())&&t.p.getHealth()>0,TribulationManager.statusJson(t.p));
                int stable=profile.stability();drive(t.p);drive(t.p);check(t.name+"_no_repeat_penalty",CultivationService.getProfile(t.p).stability()==stable,TribulationManager.statusJson(t.p));
                if(t.name.equals("low_health_failure"))check("no_failure_heal",close(t.p.getHealth(),1),t.p.getHealth());
            }else if(t.name.equals("rejected_hit")){
                check("immune_hit_has_no_credit_or_promotion",a.acceptedHits()==0&&profile.foundationQuality()==0&&!a.readyToSucceed(),TribulationManager.statusJson(t.p));
            }else{
                check(t.name+"_full_count_and_grade",a.clock().phase()==TribulationAttempt.Phase.SUCCEEDED&&a.acceptedHits()==a.rules().requiredHits()
                        &&profile.foundationQuality()==(t.kind==TribulationKind.HEAVEN?4:1)&&profile.goldenCoreQuality()==(t.kind==TribulationKind.HEAVEN?0:t.kind.quality()),TribulationManager.statusJson(t.p));
                check(t.name+"_no_refill",profile.currentSpiritualPower()==17&&t.p.getHealth()<=t.health&&profile.stability()==t.startingStability/2,StatsRuntime.info(t.p));
                var settled=profile;drive(t.p);check(t.name+"_once_settlement",CultivationService.getProfile(t.p).equals(settled),TribulationManager.statusJson(t.p));
            }
            DONE.add(t.name);
        }
    }
    private static void persistActive(MinecraftServer server)throws Exception{
        if(persisted==null){persisted=player(server.overworld(),"Trial_persist",TribulationKind.GOLDEN_HIGH);check("persist_start",TribulationManager.start(persisted,TribulationKind.GOLDEN_HIGH),TribulationManager.statusJson(persisted));}
        drive(persisted);var a=TribulationManager.load(persisted);
        if(a.acceptedHits()<1)return;
        NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(persisted));a=TribulationManager.load(persisted);
        check("logout_pauses_same_trial",a.clock().paused()&&a.acceptedHits()==1&&a.clock().nextStrike()==1,TribulationManager.statusJson(a));
        Files.createDirectories(OUT);Files.writeString(OUT.resolve("before-restart.json"),TribulationManager.statusJson(a).toString());
        var tag=new CompoundTag();persisted.saveWithoutId(tag);NbtIo.writeCompressed(tag,OUT.resolve("active-player.nbt"));DONE.add("save_active_trial");
    }
    private static void restart(MinecraftServer server)throws Exception{
        if(persisted==null){
            var l=server.overworld();for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){l.setChunkForced(x,z,true);l.getChunk(x,z);}
            persisted=player(l,"Trial_persist",TribulationKind.GOLDEN_HIGH);persisted.load(NbtIo.readCompressed(OUT.resolve("active-player.nbt"),NbtAccounter.unlimitedHeap()));
            var before=com.google.gson.JsonParser.parseString(Files.readString(OUT.resolve("before-restart.json"))).getAsJsonObject();
            NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedInEvent(persisted));var after=TribulationManager.statusJson(persisted);
            check("restart_identity_grade_and_hash",before.get("attempt_id").equals(after.get("attempt_id"))&&before.get("rules_sha256").equals(after.get("rules_sha256"))
                    &&before.get("plan_sha256").equals(after.get("plan_sha256"))&&after.get("main_completed").getAsInt()==1&&after.get("next_strike").getAsInt()==1&&after.get("kind").getAsString().equals("golden_high"),after);
            DONE.add("restart_same_trial_no_replay");
        }
        drive(persisted);var a=TribulationManager.load(persisted);
        if(!a.active()){
            var p=CultivationService.getProfile(persisted);check("restart_finishes_original_twelve_hit_grade",a.clock().phase()==TribulationAttempt.Phase.SUCCEEDED&&a.acceptedHits()==12&&p.foundationQuality()==1&&p.goldenCoreQuality()==3&&close(persisted.getMaxHealth(),124)&&p.currentSpiritualPower()==17,TribulationManager.statusJson(persisted));
            var prior=p;drive(persisted);check("restart_cannot_reward_twice",prior.equals(CultivationService.getProfile(persisted)),StatsRuntime.info(persisted));DONE.add("finish_persisted_grade_once");
        }
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post e){
        if(!ENABLED||finished||++ticks<45)return;
        try{
            if(RESTART)restart(e.getServer());
            else{if(ticks==45)setup(e.getServer());tickTrials();if(TRIALS.stream().allMatch(t->t.done))persistActive(e.getServer());}
            if(DONE.containsAll(SELECTED))finish(e.getServer());
            else if(ticks>2200){FAILED.add("timeout: "+SELECTED.stream().filter(s->!DONE.contains(s)).toList());finish(e.getServer());}
        }catch(Throwable ex){ex.printStackTrace();FAILED.add("exception: "+ex);finish(e.getServer());}
    }
    private static void finish(MinecraftServer server){
        finished=true;for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)server.overworld().setChunkForced(x,z,false);try{Files.createDirectories(OUT);Files.writeString(OUT.resolve(RESTART?"restart.json":"initial.json"),new GsonBuilder().setPrettyPrinting().create().toJson(Map.of("selected",SELECTED,"completed",DONE,"failed",FAILED,"unrun",SELECTED.stream().filter(s->!DONE.contains(s)).toList(),"checks",CHECKS,"pass",FAILED.isEmpty()&&DONE.containsAll(SELECTED),"evidence_kind","native_server_fixture_default_timeline_not_mapped_input")));}catch(Exception ex){ex.printStackTrace();}server.halt(false);
    }
}
