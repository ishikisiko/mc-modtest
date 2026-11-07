package com.example.myvillage.cultivation.fate;

import com.example.myvillage.cultivation.*;
import com.example.myvillage.cultivation.network.CultivationSnapshotPayload;
import com.example.myvillage.cultivation.time.CultivationTimeRuntime;
import com.example.myvillage.stats.runtime.*;
import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.minecraft.network.*;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.server.network.*;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** 有界原生服务器夹具，复用 spiritBodiesEvidence 的禁止打包边界；不是玩家输入证据。 */
@EventBusSubscriber(modid="myvillage")
public final class EverlastingEvidence {
    private static final String PHASE=System.getenv("MYVILLAGE_EVERLASTING_PHASE");
    private static final Path OUT=Path.of(System.getenv().getOrDefault("MYVILLAGE_EVERLASTING_OUT","../reports/everlasting-native"));
    private static final List<Map<String,Object>> CHECKS=new ArrayList<>();
    private static final List<String> FAILURES=new ArrayList<>();
    private static final UUID OWNER=UUID.nameUUIDFromBytes("everlasting-native-owner".getBytes(StandardCharsets.UTF_8));
    private static ServerPlayer owner,observer;
    private static EverlastingState expected;
    private static double beforeMaximum,beforeHealth;
    private static int start=-1;
    private static boolean done;
    private static ResourceLocation id(String name){return ResourceLocation.parse("myvillage:"+name);}
    private static void check(String name,boolean pass,Object detail){
        CHECKS.add(Map.of("name",name,"pass",pass,"detail",String.valueOf(detail)));
        if(!pass)throw new IllegalStateException(name+": "+detail);
    }
    private static boolean near(double a,double b){return Math.abs(a-b)<0.00001;}
    private static final class MemoryConnection extends ServerGamePacketListenerImpl {
        final List<CultivationSnapshotPayload> snapshots=new ArrayList<>();
        MemoryConnection(MinecraftServer server,ServerPlayer player){super(server,new Connection(PacketFlow.SERVERBOUND),player,CommonListenerCookie.createInitial(player.getGameProfile(),false));}
        @Override public boolean hasChannel(ResourceLocation channel){return true;}
        @Override public void send(Packet<?> packet){if(packet instanceof ClientboundCustomPayloadPacket custom&&custom.payload() instanceof CultivationSnapshotPayload state)snapshots.add(state);}
        @Override public void send(Packet<?> packet,PacketSendListener listener){send(packet);}
    }
    @SuppressWarnings("unchecked") private static void attach(MinecraftServer server,ServerPlayer player)throws Exception {
        var ids=PlayerList.class.getDeclaredField("playersByUUID");ids.setAccessible(true);((Map<UUID,ServerPlayer>)ids.get(server.getPlayerList())).put(player.getUUID(),player);
        var players=PlayerList.class.getDeclaredField("players");players.setAccessible(true);((List<ServerPlayer>)players.get(server.getPlayerList())).add(player);
        player.serverLevel().addNewPlayer(player);
    }
    private static ServerPlayer fresh(MinecraftServer server,UUID uuid,String name){
        var player=new ServerPlayer(server,server.overworld(),new GameProfile(uuid,name),ClientInformation.createDefault());
        player.connection=new MemoryConnection(server,player);player.gameMode.changeGameModeForPlayer(GameType.CREATIVE);
        player.getFoodData().setFoodLevel(10);player.getFoodData().setSaturation(0);player.moveTo(0.5,-59,0.5,0,0);
        return player;
    }
    private static void profile(ServerPlayer player,String realm,String stage){
        var next=CultivationService.getProfile(player).withRealmAndStage(id(realm),id(stage));
        if(realm.equals("foundation_establishment")||realm.equals("golden_core")) next=next.withFoundationQuality(1);
        if(realm.equals("golden_core")) next=next.withGoldenCoreQuality(1);
        var result=CultivationService.replaceProfile(player,next);
        if(!result.success())throw new IllegalStateException(result.message());
    }
    private static void modifier(ServerPlayer player,net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,String name,double value){
        var instance=player.getAttribute(attribute);var key=id("everlasting_evidence_"+name);instance.removeModifier(key);
        instance.addTransientModifier(new AttributeModifier(key,value,AttributeModifier.Operation.ADD_VALUE));
    }
    private static void begin(MinecraftServer server)throws Exception {
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"myvillage world pause");
        owner=fresh(server,OWNER,"EverOwner");attach(server,owner);
        var root=new SpiritualRoot(Map.of(id("fire"),10000));
        var initialized=CultivationService.replaceProfile(owner,CultivationProfile.defaultProfile().withSpiritualRoot(root));
        check("root_fixture",initialized.success(),initialized.message());
        var ids=List.of("myvillage:everlasting_embryo","myvillage:robust","myvillage:bright");
        CultivationService.replaceFates(owner,new FateState(1,1,ids,"",ids,true,0,0,0,0),true);
        check("confirmed_mortal_lifespan_120",CultivationTimeRuntime.statusFor(owner).maximumLifespanTicks()/CultivationTimeRuntime.statusFor(owner).ticksPerYear()==120,CultivationTimeRuntime.statusFor(owner));
        owner.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        for(int i=0;i<100;i++)EverlastingRuntime.eligibleTick(owner);
        check("mortal_does_not_bank_growth",EverlastingRuntime.state(owner).equals(EverlastingState.EMPTY),EverlastingRuntime.state(owner));
        owner.gameMode.changeGameModeForPlayer(GameType.CREATIVE);
        profile(owner,"qi_refining","qi_refining_1");
        var time=CultivationTimeRuntime.statusFor(owner);long year=time.ticksPerYear();
        check("qi_lifespan_180",time.maximumLifespanTicks()/year==180,time);
        observer=fresh(server,UUID.nameUUIDFromBytes("everlasting-observer".getBytes(StandardCharsets.UTF_8)),"EverObserver");attach(server,observer);
        CultivationService.replaceProfile(observer,CultivationProfile.defaultProfile().withSpiritualRoot(root).withRealmAndStage(id("qi_refining"),id("qi_refining_1")));
        check("unselected_lifespan_stays_120",CultivationTimeRuntime.statusFor(observer).maximumLifespanTicks()/year==120,CultivationTimeRuntime.statusFor(observer));
        modifier(owner,Attributes.MAX_HEALTH,"health",10);modifier(owner,Attributes.ATTACK_DAMAGE,"attack",7);modifier(owner,ModStatsAttributes.BODY_DEFENSE,"defense",2);
        FateRuntime.refresh(owner);double max=owner.getMaxHealth(),attack=owner.getAttributeValue(Attributes.ATTACK_DAMAGE),defense=owner.getAttributeValue(ModStatsAttributes.BODY_DEFENSE);
        owner.setHealth((float)max-5);double health=owner.getHealth();
        ((MemoryConnection)observer.connection).snapshots.clear();
        var grown=EverlastingState.EMPTY.advance(EverlastingRules.get().at("myvillage:qi_refining"),year,year);
        CultivationService.replaceEverlasting(owner,grown,true);
        check("health_is_independent_addition",near(owner.getMaxHealth(),max+4),List.of(max,owner.getMaxHealth()));
        check("attack_is_independent_addition",near(owner.getAttributeValue(Attributes.ATTACK_DAMAGE),attack+0.5),owner.getAttributeValue(Attributes.ATTACK_DAMAGE));
        check("defense_is_independent_addition",near(owner.getAttributeValue(ModStatsAttributes.BODY_DEFENSE),defense+0.5),owner.getAttributeValue(ModStatsAttributes.BODY_DEFENSE));
        for(int i=0;i<10;i++)StatsRuntime.refreshPlayer(owner);
        check("refresh_is_idempotent_and_does_not_heal",near(owner.getMaxHealth(),max+4)&&near(owner.getHealth(),health),List.of(owner.getMaxHealth(),owner.getHealth()));
        check("private_snapshot_carries_owner_growth",((MemoryConnection)owner.connection).snapshots.getLast().everlasting().equals(grown),grown);
        check("observer_receives_no_owner_growth",((MemoryConnection)observer.connection).snapshots.isEmpty(),((MemoryConnection)observer.connection).snapshots.size());
        var previous=EverlastingRuntime.state(owner);EverlastingRuntime.eligibleTick(owner);
        check("creative_does_not_grow",previous.equals(EverlastingRuntime.state(owner)),EverlastingRuntime.state(owner));
        owner.gameMode.changeGameModeForPlayer(GameType.SPECTATOR);EverlastingRuntime.eligibleTick(owner);
        check("spectator_does_not_grow",previous.equals(EverlastingRuntime.state(owner)),EverlastingRuntime.state(owner));
        owner.gameMode.changeGameModeForPlayer(GameType.CREATIVE);
        profile(owner,"foundation_establishment","foundation_early");
        check("breakthrough_keeps_growth",previous.equals(EverlastingRuntime.state(owner)),EverlastingRuntime.state(owner));
        check("foundation_lifespan_360",CultivationTimeRuntime.statusFor(owner).maximumLifespanTicks()/year==360,CultivationTimeRuntime.statusFor(owner));
        profile(owner,"golden_core","golden_early");
        check("golden_lifespan_750",CultivationTimeRuntime.statusFor(owner).maximumLifespanTicks()/year==750,CultivationTimeRuntime.statusFor(owner));
        profile(owner,"qi_refining","qi_refining_1");
        var normal=CultivationService.getProfile(owner);
        CultivationService.replaceProfile(owner,normal.withLifespanConsumedTicks(CultivationTimeRuntime.statusFor(owner).maximumLifespanTicks()));
        owner.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);EverlastingRuntime.eligibleTick(owner);
        check("exhaustion_stops_new_growth",previous.equals(EverlastingRuntime.state(owner)),EverlastingRuntime.state(owner));
        CultivationService.replaceProfile(owner,normal);owner.gameMode.changeGameModeForPlayer(GameType.CREATIVE);
        expected=grown.advance(EverlastingRules.get().at("myvillage:qi_refining"),year,12345);
        CultivationService.replaceEverlasting(owner,expected,true);
        owner.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);owner.hurt(owner.damageSources().genericKill(),1000000);
        check("ordinary_death_occurs",!owner.isAlive(),owner.getHealth());
        owner=server.getPlayerList().respawn(owner,false,Entity.RemovalReason.KILLED);
        owner.connection=new MemoryConnection(server,owner);owner.gameMode.changeGameModeForPlayer(GameType.CREATIVE);
        check("native_copy_on_death_preserves_credits_and_remainder",expected.equals(EverlastingRuntime.state(owner)),EverlastingRuntime.state(owner));
        server.getPlayerList().remove(owner);owner=null;
    }
    private static void rejoin(MinecraftServer server)throws Exception {
        owner=fresh(server,OWNER,"EverOwner");var loaded=server.getPlayerList().load(owner);
        check("logout_saved_real_player_file",loaded.isPresent(),loaded.isPresent());
        check("rejoin_does_not_reset_or_add_offline_growth",expected.equals(EverlastingRuntime.state(owner)),EverlastingRuntime.state(owner));
        attach(server,owner);owner.gameMode.changeGameModeForPlayer(GameType.CREATIVE);
        long year=CultivationTimeRuntime.statusFor(owner).ticksPerYear();
        CultivationService.replaceEverlasting(owner,EverlastingState.EMPTY.advance(EverlastingRules.get().at("myvillage:qi_refining"),year,year/8-1),true);
        beforeMaximum=owner.getMaxHealth();owner.setHealth((float)beforeMaximum-3);beforeHealth=owner.getHealth();
        owner.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
    }
    private static void afterRealTick(MinecraftServer server)throws Exception {
        check("real_server_tick_credits_exact_half_point",EverlastingRuntime.state(owner).health().units()==1,EverlastingRuntime.state(owner));
        check("real_tick_adds_maximum_without_healing",near(owner.getMaxHealth(),beforeMaximum+0.5)&&near(owner.getHealth(),beforeHealth),List.of(owner.getMaxHealth(),owner.getHealth()));
        owner.gameMode.changeGameModeForPlayer(GameType.CREATIVE);expected=EverlastingRuntime.state(owner);
        Files.createDirectories(OUT);Files.writeString(OUT.resolve("expected.json"),EverlastingState.CODEC.encodeStart(JsonOps.INSTANCE,expected).getOrThrow().toString());
        server.getPlayerList().saveAll();finish(server);
    }
    private static void restarted(MinecraftServer server)throws Exception {
        expected=EverlastingState.CODEC.parse(JsonOps.INSTANCE,JsonParser.parseString(Files.readString(OUT.resolve("expected.json")))).getOrThrow();
        owner=fresh(server,OWNER,"EverOwner");check("restart_loads_player_file",server.getPlayerList().load(owner).isPresent(),OWNER);
        check("restart_preserves_credits_and_partial_progress",expected.equals(EverlastingRuntime.state(owner)),EverlastingRuntime.state(owner));
        attach(server,owner);owner.gameMode.changeGameModeForPlayer(GameType.CREATIVE);StatsRuntime.refreshPlayer(owner);
        var before=EverlastingRuntime.state(owner);EverlastingRuntime.eligibleTick(owner);
        check("restart_does_not_grant_offline_income",before.equals(EverlastingRuntime.state(owner)),before);
        check("restart_restores_single_health_modifier",owner.getAttribute(Attributes.MAX_HEALTH).getModifier(EverlastingRuntime.HEALTH)!=null,owner.getMaxHealth());
        long year=CultivationTimeRuntime.statusFor(owner).ticksPerYear();
        owner.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);EverlastingRuntime.eligibleTick(owner);owner.gameMode.changeGameModeForPlayer(GameType.CREATIVE);
        check("restart_continues_from_saved_fraction",expected.advance(EverlastingRules.get().at("myvillage:qi_refining"),year,1).equals(EverlastingRuntime.state(owner)),EverlastingRuntime.state(owner));
        finish(server);
    }
    private static void save()throws Exception {
        Files.createDirectories(OUT);var report=new LinkedHashMap<String,Object>();
        report.put("phase",PHASE);report.put("checks",CHECKS);report.put("failed",FAILURES);report.put("pass",FAILURES.isEmpty());
        report.put("input_evidence","native_server_fixture_no_player_input");
        Files.writeString(OUT.resolve(PHASE+".json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));
    }
    private static void finish(MinecraftServer server)throws Exception {done=true;save();server.halt(false);}
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void tick(ServerTickEvent.Post event) {
        if(PHASE==null||done)return;
        try {
            if(start<0){start=event.getServer().getTickCount();if(PHASE.equals("initial"))begin(event.getServer());else restarted(event.getServer());}
            int frame=event.getServer().getTickCount()-start;
            if(PHASE.equals("initial")&&frame==5)rejoin(event.getServer());
            if(PHASE.equals("initial")&&frame==6)afterRealTick(event.getServer());
        } catch(Throwable error){error.printStackTrace();FAILURES.add(error.toString());try{finish(event.getServer());}catch(Exception ignored){done=true;event.getServer().halt(false);}}
    }
}
