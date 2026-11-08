package com.example.myvillage.sim.runtime.player;

import com.example.myvillage.cultivation.*;
import com.example.myvillage.cultivation.time.*;
import com.example.myvillage.sim.*;
import com.example.myvillage.sim.runtime.*;
import com.example.myvillage.spiritstone.*;
import com.example.myvillage.stats.*;
import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import java.math.BigInteger;
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
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.*;

/** 仅用于独立新世界原生回执与重启检查，不进入交付包，不代表玩家操作验收。 */
@EventBusSubscriber(modid="myvillage")
public final class SectProgressionEvidence {
    private static final String PHASE = System.getenv("MYVILLAGE_SECT_PHASE");
    private static final Path OUT = Path.of(System.getenv().getOrDefault("MYVILLAGE_SECT_OUT", "../reports/sect-native"));
    private static final UUID OWNER = UUID.nameUUIDFromBytes("sect-proof-owner".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    private static final List<Map<String,Object>> checks = new ArrayList<>();
    private static final List<String> failures = new ArrayList<>();
    private static ServerPlayer owner, other;
    private static int stage, stopAt = -1;
    private static int sectId;
    private static int readyBy;
    private static com.example.myvillage.sim.sect.ElderAssessment.Trial first, current;
    private static Mob stale;
    private static List<Map<String,Object>> leaders = List.of();
    private static void check(String id, boolean ok, Object detail) {
        checks.add(Map.of("id",id,"pass",ok,"detail",String.valueOf(detail)));
        if (!ok) throw new IllegalStateException(id+": "+detail);
    }
    private static final class MemoryConnection extends ServerGamePacketListenerImpl {
        MemoryConnection(MinecraftServer s, ServerPlayer p) { super(s,new Connection(PacketFlow.SERVERBOUND),p,CommonListenerCookie.createInitial(p.getGameProfile(),false)); }
        @Override public boolean hasChannel(ResourceLocation channel) { return true; }
        @Override public void send(Packet<?> packet) { }
        @Override public void send(Packet<?> packet, PacketSendListener listener) { }
    }
    @SuppressWarnings("unchecked") private static ServerPlayer actor(MinecraftServer server, UUID id, String name, boolean load) throws Exception {
        var p = new ServerPlayer(server,server.overworld(),new GameProfile(id,name),ClientInformation.createDefault());
        p.connection = new MemoryConnection(server,p);
        if (load) check("player_file_loaded",server.getPlayerList().load(p).isPresent(),id);
        var ids=PlayerList.class.getDeclaredField("playersByUUID");ids.setAccessible(true);((Map<UUID,ServerPlayer>)ids.get(server.getPlayerList())).put(id,p);
        var players=PlayerList.class.getDeclaredField("players");players.setAccessible(true);((List<ServerPlayer>)players.get(server.getPlayerList())).add(p);
        p.moveTo(0.5,65,0.5,0,0);p.serverLevel().addNewPlayer(p);p.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        return p;
    }
    private static WorldSim sim() { return WorldSimRuntime.sim().orElseThrow(); }
    private static void advanceMonth(MinecraftServer server) {
        var calendar=CultivationCalendarSavedData.get(server.overworld());var scale=CultivationServerConfig.scale();
        long ticks=(long)scale.ticksPerDay()*scale.daysPerMonth();long until=(calendar.elapsedCalendarTicks()/ticks+1)*ticks;
        while(calendar.elapsedCalendarTicks()<until)calendar.incrementSaturated();calendar.checkpoint();
    }
    private static BigInteger low(ServerPlayer p) {
        var item=new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.parse("myvillage:low_grade_spirit_stone")));
        return SpiritStoneSources.open(p).copies().stream().map(stack->SpiritStoneStorage.count(stack,SpiritStoneStorage.ordinary(item)))
                .reduce(BigInteger.ZERO,BigInteger::add);
    }
    private static void begin(MinecraftServer server) throws Exception {
        WorldSimRuntime.setPaused(true);
        leaders = sim().sects(false).stream().map(s -> {
            var p = sim().person(s.masterId()).orElseThrow();
            return Map.<String,Object>of("sect",s.name(),"tier",s.tierId(),"region",s.regionId(),"realm",p.realmId(),"stage",p.stage());
        }).toList();
        var level=server.overworld();
        for(int cx=-1;cx<=1;cx++)for(int cz=-1;cz<=1;cz++){level.setChunkForced(cx,cz,true);level.getChunk(cx,cz);}
        for(int x=-12;x<=12;x++)for(int z=-12;z<=12;z++){
            level.setBlock(new BlockPos(x,64,z),Blocks.STONE.defaultBlockState(),3);
            for(int y=65;y<=69;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3);
        }
        owner=actor(server,OWNER,"SectOwner",false);
        other=actor(server,UUID.nameUUIDFromBytes("sect-proof-other".getBytes()),"SectOther",false);
        var profile=CultivationProfile.defaultProfile().withSpiritualRoot(new SpiritualRoot(Map.of(ResourceLocation.parse("myvillage:metal"),60)))
                .withRealmAndStage(ResourceLocation.parse("myvillage:foundation_establishment"),ResourceLocation.parse("myvillage:foundation_early")).withFoundationQuality(1);
        check("profile_fixture",CultivationService.replaceProfile(owner,profile).success(),profile);
        owner.setHealth(owner.getMaxHealth());
        var home=sim().sects(false).stream().filter(s->s.tierId().equals("branch")).findFirst().orElseThrow();sectId=home.id();
        sim().joinSect(OWNER.toString(),"SectOwner",sectId,WorldSimPlayers.qualification(owner),true);
        sim().promotePlayer(OWNER.toString(),"SectOwner","inner");
        check("no_supply_on_join",sim().pendingStipend(OWNER.toString()).isEmpty(),sim().pendingStipend(OWNER.toString()));
        var npc=com.example.myvillage.entity.ModEntities.CULTIVATOR.get().create(level);
        com.example.myvillage.stats.runtime.StatsRuntime.applyRealm(npc,"spirit_transformation",2);
        var body=CultivationStatsRules.bundled().at(new RealmKey("spirit_transformation",2),npc.foundationQuality(),npc.goldenCoreQuality());
        check("spirit_transformation_native_body",Math.abs(npc.getMaxHealth()-body.health())<.001,npc.getMaxHealth());npc.discard();
        advanceMonth(server);readyBy=server.getTickCount()+200;stage=1;
    }
    private static void claimAndFight(MinecraftServer server) throws Exception {
        boolean ready = true;
        for(int cx=-1;cx<=1;cx++)for(int cz=-1;cz<=1;cz++)
            ready &= owner.serverLevel().isPositionEntityTicking(new BlockPos(cx*16,65,cz*16));
        if (!ready && server.getTickCount()<readyBy) return;
        check("trial_chunks_entity_ticking",ready,server.getTickCount());
        check("calendar_month_credits_inner_10",sim().pendingStipend(OWNER.toString()).equals(Map.of("low",BigInteger.TEN)),sim().pendingStipend(OWNER.toString()));
        for(int i=0;i<36;i++)owner.getInventory().setItem(i,new ItemStack(Items.COBBLESTONE,64));
        SectBenefitsRuntime.claim(owner,sim(),sectId);
        check("full_inventory_keeps_receivable",sim().pendingStipend(OWNER.toString()).equals(Map.of("low",BigInteger.TEN)),sim().pendingStipend(OWNER.toString()));
        owner.getInventory().setItem(0,ItemStack.EMPTY);SectBenefitsRuntime.claim(owner,sim(),sectId);
        check("native_inventory_receives_10",low(owner).equals(BigInteger.TEN)&&sim().pendingStipend(OWNER.toString()).isEmpty(),low(owner));
        SectBenefitsRuntime.claim(owner,sim(),sectId);check("repeat_claim_gives_nothing",low(owner).equals(BigInteger.TEN),low(owner));
        check("trial_available_after_foundation",sim().elderTrialRefusal(OWNER.toString()).isEmpty(),sim().playerMember(OWNER.toString()));
        SectBenefitsRuntime.startTrial(owner,sim());first=sim().elderTrial(OWNER.toString()).orElseThrow();stage=2;
    }
    private static void replaceTrial(MinecraftServer server) {
        stale=(Mob)owner.serverLevel().getEntity(UUID.fromString(first.target()));
        check("first_opponent_registered",stale!=null,first);stale.setNoAi(true);
        sim().cancelElderTrial(OWNER.toString());SectBenefitsRuntime.startTrial(owner,sim());
        current=sim().elderTrial(OWNER.toString()).orElseThrow();stage=3;
    }
    private static void fight(MinecraftServer server) {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(stale));
        check("old_opponent_cannot_cancel_new_trial",sim().elderTrial(OWNER.toString()).orElseThrow().equals(current),current);
        Mob target=(Mob)owner.serverLevel().getEntity(UUID.fromString(current.target()));
        check("current_opponent_registered",target!=null,current);target.setNoAi(true);
        var assigned=target.getPersistentData().getCompound("myvillage:stats_realm");
        check("opponent_is_qi_peak",assigned.getString("realm").endsWith("qi_refining")&&assigned.getInt("stage")==8,assigned);
        float hp=target.getHealth();target.hurt(other.damageSources().playerAttack(other),1000);
        check("other_player_cannot_damage_trial",target.getHealth()==hp,target.getHealth());
        target.hurt(target.damageSources().fall(),1000);check("environment_cannot_complete_trial",target.getHealth()==hp,target.getHealth());
        target.setHealth(1);target.invulnerableTime=0;target.hurt(owner.damageSources().playerAttack(owner),1024);
        check("actual_death_promotes_owner",sim().playerMember(OWNER.toString()).orElseThrow().rank().equals("elder")&&sim().elderTrial(OWNER.toString()).isEmpty(),sim().playerMember(OWNER.toString()));
        check("other_player_not_promoted",sim().playerMember(other.getUUID().toString()).isEmpty(),other.getUUID());
        advanceMonth(server);stage=4;
    }
    private static void saveForRestart(MinecraftServer server) throws Exception {
        check("elder_receives_40_next_month",sim().pendingStipend(OWNER.toString()).equals(Map.of("low",BigInteger.valueOf(40))),sim().pendingStipend(OWNER.toString()));
        check("npc_master_supply_recorded",sim().sects(false).stream().anyMatch(s->!sim().npcStipend(s.masterId()).isEmpty()),"NPC账户已累计供奉");
        Files.createDirectories(OUT);Files.writeString(OUT.resolve("expected.json"),new Gson().toJson(Map.of("sect",sectId,"pending_low",40,"inventory_low",10)));
        server.getPlayerList().saveAll();WorldSimSavedData.get(server.overworld()).setDirty();finish(server);
    }
    private static void restart(MinecraftServer server) throws Exception {
        WorldSimRuntime.setPaused(true);var expected=JsonParser.parseString(Files.readString(OUT.resolve("expected.json"))).getAsJsonObject();
        sectId=expected.get("sect").getAsInt();
        check("rank_survives_real_restart",sim().playerMember(OWNER.toString()).orElseThrow().rank().equals("elder"),sim().playerMember(OWNER.toString()));
        check("unclaimed_supply_survives_real_restart",sim().pendingStipend(OWNER.toString()).equals(Map.of("low",BigInteger.valueOf(40))),sim().pendingStipend(OWNER.toString()));
        owner=actor(server,OWNER,"SectOwner",true);check("claimed_inventory_survives_restart",low(owner).equals(BigInteger.TEN),low(owner));
        var scale=CultivationServerConfig.scale();long month=CultivationCalendarSavedData.get(server.overworld()).elapsedCalendarTicks()/scale.ticksPerDay()/scale.daysPerMonth();
        check("same_month_does_not_recredit",!sim().settleSectMonth(month),month);
        SectBenefitsRuntime.claim(owner,sim(),sectId);check("pending_supply_claims_once_after_restart",low(owner).equals(BigInteger.valueOf(50))&&sim().pendingStipend(OWNER.toString()).isEmpty(),low(owner));
        SectBenefitsRuntime.claim(owner,sim(),sectId);check("restart_repeat_claim_is_idempotent",low(owner).equals(BigInteger.valueOf(50)),low(owner));
        finish(server);
    }
    private static void finish(MinecraftServer server) throws Exception {
        Files.createDirectories(OUT);Files.writeString(OUT.resolve(PHASE+".json"),new GsonBuilder().setPrettyPrinting().create().toJson(Map.of("phase",PHASE,"pass",failures.isEmpty(),"checks",checks,"failures",failures,"mapped_input","not_verified","initial_leaders",leaders)));
        stopAt=server.getTickCount()+5;
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if(PHASE==null)return;var server=event.getServer();
        if(stopAt>=0){if(server.getTickCount()>=stopAt)server.halt(false);return;}
        if(server.getTickCount()<80)return;
        try{
            if(PHASE.equals("restart")){restart(server);return;}
            if(stage==0)begin(server);else if(stage==1)claimAndFight(server);else if(stage==2)replaceTrial(server);else if(stage==3)fight(server);else saveForRestart(server);
        }catch(Throwable error){error.printStackTrace();failures.add(error.toString());try{finish(server);}catch(Exception nested){server.halt(false);}}
    }
}
