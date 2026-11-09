package com.example.myvillage.cultivation.fate;

import com.example.myvillage.cultivation.*;
import com.example.myvillage.cultivation.data.ModCultivationRegistries;
import com.example.myvillage.cultivation.growth.*;
import com.example.myvillage.cultivation.meditation.MeditationManager;
import com.example.myvillage.cultivation.study.*;
import com.example.myvillage.combat.session.CombatSessionManager;
import com.example.myvillage.sim.runtime.WorldSimRuntime;
import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.*;
import net.minecraft.network.protocol.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.server.network.*;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** 实际会话、法术命中及停服重启回执；未映射玩家输入。 */
@EventBusSubscriber(modid="myvillage")
public final class SchoolInsightEvidence {
    private static final String PHASE=System.getenv("MYVILLAGE_SCHOOL_PHASE");
    private static final Path OUT=Path.of(System.getenv().getOrDefault("MYVILLAGE_SCHOOL_OUT","../reports/school-native"));
    private static final UUID ID=UUID.nameUUIDFromBytes("school-insight-proof".getBytes());
    private static final ResourceLocation FIELD=ResourceLocation.parse("myvillage:chiyan_jie"),OTHER=ResourceLocation.parse("myvillage:shuiren_shu");
    private static final List<Map<String,Object>> CHECKS=new ArrayList<>();
    private static final List<String> FAILURES=new ArrayList<>();
    private static ServerPlayer player;private static Zombie target;
    private static int stage,nextTick,stopAt=-1;private static long token,revision;
    private static void check(String id,boolean pass,Object detail){CHECKS.add(Map.of("id",id,"pass",pass,"detail",String.valueOf(detail)));if(!pass)throw new IllegalStateException(id+": "+detail);}
    private static final class MemoryConnection extends ServerGamePacketListenerImpl {
        MemoryConnection(MinecraftServer s,ServerPlayer p){super(s,new Connection(PacketFlow.SERVERBOUND),p,CommonListenerCookie.createInitial(p.getGameProfile(),false));}
        @Override public boolean hasChannel(ResourceLocation c){return true;}
        @Override public void send(Packet<?> p){}
        @Override public void send(Packet<?> p,PacketSendListener l){}
    }
    @SuppressWarnings("unchecked") private static ServerPlayer actor(MinecraftServer server,boolean load)throws Exception{
        var p=new ServerPlayer(server,server.overworld(),new GameProfile(ID,"SchoolProof"),ClientInformation.createDefault());p.connection=new MemoryConnection(server,p);
        if(load)check("player_file_load",server.getPlayerList().load(p).isPresent(),ID);
        var ids=PlayerList.class.getDeclaredField("playersByUUID");ids.setAccessible(true);((Map<UUID,ServerPlayer>)ids.get(server.getPlayerList())).put(ID,p);
        var ps=PlayerList.class.getDeclaredField("players");ps.setAccessible(true);((List<ServerPlayer>)ps.get(server.getPlayerList())).add(p);
        p.moveTo(.5,65,.5,0,0);p.serverLevel().addNewPlayer(p);p.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);return p;
    }
    private static void prepare(MinecraftServer server)throws Exception{
        WorldSimRuntime.setPaused(true);var level=server.overworld();
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){level.setChunkForced(x,z,true);level.getChunk(x,z);}
        for(int x=-10;x<=10;x++)for(int z=-10;z<=10;z++){level.setBlock(new BlockPos(x,64,z),Blocks.STONE.defaultBlockState(),3);for(int y=65;y<70;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3);}
        player=actor(server,false);
        var profile=CultivationProfile.defaultProfile().withSpiritualRoot(new SpiritualRoot(Map.of(ResourceLocation.parse("myvillage:fire"),100,ResourceLocation.parse("myvillage:water"),100)))
                .withRealmAndStage(ResourceLocation.parse("myvillage:foundation_establishment"),ResourceLocation.parse("myvillage:foundation_early")).withFoundationQuality(1)
                .learnTechnique(FIELD).learnTechnique(OTHER).learnTechnique(ResourceLocation.parse("myvillage:linghuo_shu"));
        check("profile_installed",CultivationService.replaceProfile(player,profile).success(),profile);
        var candidates=List.of("myvillage:school_insight","myvillage:robust","myvillage:lightfoot","myvillage:bright","myvillage:herbal_lore","myvillage:fire_affinity");
        CultivationService.replaceFates(player,FateState.empty().choice(candidates,"",List.of(),false,1),false);revision=FateService.state(player).revision();
        var stele=new BlockPos(1,65,0);level.setBlock(stele,com.example.myvillage.block.ModBlocks.SPIRIT_TESTING_STELE.get().defaultBlockState(),3);
        token=SteleAwakeningSessions.open(player,stele);check("real_stele_session",token>0&&SteleAwakeningSessions.valid(player,token),token);
        stage=1;nextTick=server.getTickCount()+20;
    }
    private static void reject(int which){
        boolean result=switch(which){case 1->FateService.intent(player,2,revision,7,token+123,"myvillage:fire_arts");case 2->FateService.intent(player,2,revision,7,token,"");default->FateService.intent(player,2,revision,7,token,"unknown:school");};
        check("rejected_choice_"+which,!result&&!FateService.state(player).confirmed()&&SchoolInsightRuntime.state(player).school().isEmpty(),FateService.state(player));
    }
    private static void confirm(MinecraftServer server){
        check("choice_committed",FateService.intent(player,2,revision,7,token,"myvillage:fire_arts"),SchoolInsightRuntime.state(player));
        check("fixed_school",SchoolInsightRuntime.state(player).school().equals("myvillage:fire_arts"),SchoolInsightRuntime.state(player));
        check("fire_rate",SchoolInsightRuntime.bonus(player,ModCultivationRegistries.technique(player.registryAccess(),FIELD).orElseThrow(),"school_mastery")==4000,"4000bp");
        check("water_rate_unchanged",SchoolInsightRuntime.bonus(player,ModCultivationRegistries.technique(player.registryAccess(),OTHER).orElseThrow(),"school_mastery")==0,"水系");
        check("matching_study_14",study("huiyan_zhang")==14,"火系研读14");check("other_study_10",study("liushui_zhang")==10,"水系研读10");
        stage=5;nextTick=server.getTickCount()+20;
    }
    private static int study(String name){
        var id=ResourceLocation.parse("myvillage:"+name);var lookup=TechniqueGrowthRuntime.lookup(player);var def=lookup.apply(id);
        final int[] points={0};var slot=new ManualSlot(){public Optional<ResourceLocation> technique(){return Optional.of(id);}public int comprehension(){return points[0];}public void writeComprehension(int n){points[0]=n;}public void consumeOne(){}};
        var result=StudyStep.settle(id,slot,CultivationService.getProfile(player),lookup,MeditationManager.realmLookup(player),ignored->true,Optional.empty(),SchoolInsightRuntime.bonus(player,def,"school_study"),p->CultivationService.replaceProfile(player,p).success());
        check("study_accepted_"+name,result.outcome()==StudyStep.Outcome.CONTINUE,result);return points[0];
    }
    private static void cast(MinecraftServer server){
        check("confirmed_cannot_change",!FateService.intent(player,2,FateService.state(player).revision(),7,token,"myvillage:water_arts")&&SchoolInsightRuntime.state(player).school().equals("myvillage:fire_arts"),SchoolInsightRuntime.state(player));
        target=EntityType.ZOMBIE.create(server.overworld());target.setNoAi(true);target.setPersistenceRequired();target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);target.setHealth(1000);target.moveTo(.5,65,4.5,0,0);server.overworld().addFreshEntity(target);
        player.setOnGround(true);CultivationService.recoverSpiritualPower(player,CultivationService.spiritualPowerCap(player));
        if(com.example.myvillage.combat.CombatService.getMode(player)!=com.example.myvillage.combat.CombatMode.CULTIVATION)
            check("cultivation_combat_mode",com.example.myvillage.combat.CombatService.toggleMode(player),"切换修仙战斗模式");
        check("actual_fire_field_started",CombatSessionManager.handleArtIntent(player,FIELD),FIELD);
        stage=6;nextTick=server.getTickCount()+100;
    }
    private static void save(MinecraftServer server)throws Exception{
        var progress=CultivationService.getProfile(player).learnedTechniques().get(FIELD);
        check("actual_field_damages",target.getHealth()<1000,target.getHealth());
        check("multiple_pulses_award_once",progress.masteryPoints()==1&&progress.masteryRemainder()==4000,progress);
        TechniqueGrowthRuntime.hit(player,OTHER);check("other_school_base_one",CultivationService.getProfile(player).learnedTechniques().get(OTHER).masteryPoints()==1,CultivationService.getProfile(player).learnedTechniques());
        server.getPlayerList().saveAll();finish(server);
    }
    private static void restart(MinecraftServer server)throws Exception{
        WorldSimRuntime.setPaused(true);player=actor(server,true);var profile=CultivationService.getProfile(player);var progress=profile.learnedTechniques().get(FIELD);
        check("school_survives_restart",SchoolInsightRuntime.state(player).school().equals("myvillage:fire_arts"),SchoolInsightRuntime.state(player));
        check("fraction_survives_restart",progress.masteryPoints()==1&&progress.masteryRemainder()==4000,progress);
        for(int i=0;i<4;i++)TechniqueGrowthRuntime.hit(player,FIELD);
        progress=CultivationService.getProfile(player).learnedTechniques().get(FIELD);check("five_rewards_total_seven",progress.masteryPoints()==7&&progress.masteryRemainder()==0,progress);
        finish(server);
    }
    private static void finish(MinecraftServer server)throws Exception{Files.createDirectories(OUT);Files.writeString(OUT.resolve(PHASE+".json"),new GsonBuilder().setPrettyPrinting().create().toJson(Map.of("pass",FAILURES.isEmpty(),"checks",CHECKS,"failures",FAILURES,"mapped_input","not_verified")));stopAt=server.getTickCount()+5;}
    @SubscribeEvent public static void tick(ServerTickEvent.Post e){
        if(PHASE==null)return;var server=e.getServer();if(stopAt>=0){if(server.getTickCount()>=stopAt)server.halt(false);return;}if(server.getTickCount()<80||server.getTickCount()<nextTick)return;
        try{if(PHASE.equals("restart")){restart(server);return;}if(stage==0)prepare(server);else if(stage<=3){reject(stage++);nextTick=server.getTickCount()+20;}else if(stage==4)confirm(server);else if(stage==5)cast(server);else save(server);}
        catch(Throwable t){t.printStackTrace();FAILURES.add(t.toString());try{finish(server);}catch(Exception ignored){server.halt(false);}}
    }
}
