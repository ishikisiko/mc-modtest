package com.example.myvillage.estate;

import com.example.myvillage.entity.npc.NpcEntity;
import com.example.myvillage.item.ModItems;
import com.example.myvillage.npc.*;
import com.example.myvillage.spiritstone.*;
import com.example.myvillage.story.net.*;
import com.example.myvillage.sim.runtime.WorldSimRuntime;
import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.*;
import net.minecraft.network.protocol.*;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.*;
import net.minecraft.server.level.*;
import net.minecraft.server.network.*;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** 新世界、真实服务端对话、物品与重启探针；没有映射输入，不进入交付包。 */
@EventBusSubscriber(modid="myvillage")
public final class EstateEvidence {
    private static final String PHASE=System.getenv("MYVILLAGE_ESTATE_PHASE");
    private static final Path OUT=Path.of(System.getenv().getOrDefault("MYVILLAGE_ESTATE_OUT","../reports/estate-native"));
    private static final UUID OWNER=UUID.nameUUIDFromBytes("estate-proof-owner".getBytes(StandardCharsets.UTF_8));
    private static final UUID OTHER=UUID.nameUUIDFromBytes("estate-proof-other".getBytes(StandardCharsets.UTF_8));
    private static final List<Map<String,Object>> checks=new ArrayList<>();
    private static final List<String> failures=new ArrayList<>();
    private static ServerPlayer owner,other;
    private static EstateRuntime.Site site;
    private static NpcEntity npc;
    private static StoryDialoguePayload staleOther;
    private static NpcDialogueIntent paidIntent;
    private static int stage,next,stop=-1;
    private static void check(String id,boolean ok,Object detail) {
        checks.add(Map.of("id",id,"pass",ok,"detail",String.valueOf(detail)));
        if(!ok)throw new IllegalStateException(id+": "+detail);
    }
    private static final class MemoryConnection extends ServerGamePacketListenerImpl {
        StoryDialoguePayload page;
        MemoryConnection(MinecraftServer server,ServerPlayer p) { super(server,new Connection(PacketFlow.SERVERBOUND),p,CommonListenerCookie.createInitial(p.getGameProfile(),false)); }
        @Override public boolean hasChannel(ResourceLocation c) { return true; }
        @Override public void send(Packet<?> p) { capture(p); }
        @Override public void send(Packet<?> p,PacketSendListener l) { capture(p); }
        private void capture(Packet<?> p) { if(p instanceof ClientboundCustomPayloadPacket packet && packet.payload() instanceof NpcDialoguePayload data)page=data.page(); }
    }
    @SuppressWarnings("unchecked") private static ServerPlayer actor(MinecraftServer server,UUID id,String name,boolean load)throws Exception {
        var p=new ServerPlayer(server,server.overworld(),new GameProfile(id,name),ClientInformation.createDefault());
        p.connection=new MemoryConnection(server,p);
        if(load)check("player_file_"+name,server.getPlayerList().load(p).isPresent(),id);
        var ids=PlayerList.class.getDeclaredField("playersByUUID");ids.setAccessible(true);((Map<UUID,ServerPlayer>)ids.get(server.getPlayerList())).put(id,p);
        var players=PlayerList.class.getDeclaredField("players");players.setAccessible(true);((List<ServerPlayer>)players.get(server.getPlayerList())).add(p);
        p.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);p.getAbilities().invulnerable=true;
        p.serverLevel().addNewPlayer(p);return p;
    }
    private static void load(ServerLevel level,BlockPos p) { level.setChunkForced(p.getX()>>4,p.getZ()>>4,true);level.getChunk(p); }
    private static void outside(ServerPlayer p) {
        var at=site.world(site.definition().steward()).east();p.moveTo(at.getX()+.5,at.getY(),at.getZ()+.5,90,0);p.setDeltaMovement(0,0,0);
    }
    private static StoryDialoguePayload page(ServerPlayer p) { return ((MemoryConnection)p.connection).page; }
    private static boolean offered(StoryDialoguePayload p,String id) { return p!=null&&p.options().stream().anyMatch(o->o.id().equals(id)); }
    private static void choose(ServerPlayer p,String option) {
        var page=page(p);check("offered_"+option+"_"+stage,offered(page,option),page);
        var intent=new NpcDialogueIntent(new StoryIntentPayload(page.entityId(),page.nonce(),option));
        NpcDialogue.choose(p,intent);if(option.equals("estate_buy"))paidIntent=intent;
    }
    private static BigInteger stones(ServerPlayer p,Item item) { return SpiritStoneSources.open(p).copies().stream().map(s->SpiritStoneStorage.count(s,v->v.is(item))).reduce(BigInteger.ZERO,BigInteger::add); }
    private static BlockPos inside() { return site.world(new BlockPos(455,155,762)); }
    private static void begin(MinecraftServer server)throws Exception {
        WorldSimRuntime.setPaused(true);
        var sites=EstateRuntime.sites(server.overworld());check("one_estate",sites.size()==1,sites);
        site=sites.getFirst();check("generated_price",site.definition().price()==100&&site.definition().currency().toString().equals("myvillage:high_grade_spirit_stone"),site.definition());
        load(server.overworld(),site.world(site.definition().steward()));
        load(server.overworld(),inside());
        owner=actor(server,OWNER,"EstateOwner",PHASE.equals("restart"));other=actor(server,OTHER,"EstateOther",PHASE.equals("restart"));
        outside(owner);outside(other);stage=1;next=server.getTickCount()+80;
    }
    private static void gate(MinecraftServer server)throws Exception {
        var level=server.overworld();var entity=level.getEntity(site.stewardId());
        check("persistent_steward_present",entity instanceof NpcEntity,entity);npc=(NpcEntity)entity;
        check("steward_outside_private_column",!site.bounds().overlaps(npc.getX(),npc.getZ(),npc.getBbWidth()/2.0),npc.position());
        check("one_steward",level.getEntitiesOfClass(NpcEntity.class,npc.getBoundingBox().inflate(4)).stream().filter(n->n.getUUID().equals(site.stewardId())).count()==1,npc.getUUID());
        check("real_gate_floor_and_headroom",!level.getBlockState(npc.blockPosition().below()).isAir()&&level.getBlockState(npc.blockPosition()).isAir()&&level.getBlockState(npc.blockPosition().above()).isAir(),npc.blockPosition());
        check("dialogue_valid",NpcDialogue.valid(owner,npc),npc.position());
        if(PHASE.equals("restart")) { restart(server);return; }
        String manifest="/data/"+site.city().bundle().resource.replace(':','/');
        try(var input=EstateEvidence.class.getResourceAsStream(manifest)) {
            var root=JsonParser.parseReader(new java.io.InputStreamReader(input,StandardCharsets.UTF_8)).getAsJsonObject();
            var samples=root.getAsJsonObject("player_estate_build").getAsJsonArray("native_samples");
            int count=0;
            for(var value:samples) {
                var row=value.getAsJsonObject();var p=row.getAsJsonArray("position");
                var at=site.world(new BlockPos(p.get(0).getAsInt(),p.get(1).getAsInt(),p.get(2).getAsInt()));load(level,at);
                String expected=row.get("state").getAsString();
                var wanted=net.minecraft.commands.arguments.blocks.BlockStateParser.parseForBlock(level.holderLookup(net.minecraft.core.registries.Registries.BLOCK),expected,true).blockState();
                check("new_world_floor_"+count++,level.getBlockState(at).equals(wanted),at+" "+expected+" / "+level.getBlockState(at));
            }
            check("all_18_floors_sampled",count==54,count);
        }
        check("initially_unsold",EstateSavedData.get(server).titles.get(site.key())==null,site.key());
        check("unsold_teleport_denied",!owner.teleportTo(level,inside().getX()+.5,inside().getY(),inside().getZ()+.5,Set.of(),0,0),owner.position());
        owner.connection.handleMovePlayer(new ServerboundMovePlayerPacket.Pos(inside().getX()+.5,inside().getY(),inside().getZ()+.5,true));
        check("walking_packet_cannot_enter",!site.bounds().overlaps(owner.getX(),owner.getZ(),.3),owner.position());
        owner.connection.teleport(inside().getX()+.5,200,inside().getZ()+.5,0,0);
        check("pearl_style_connection_teleport_denied",!site.bounds().overlaps(owner.getX(),owner.getZ(),.3),owner.position());
        owner.move(net.minecraft.world.entity.MoverType.PISTON,new net.minecraft.world.phys.Vec3(-10,0,0));
        check("server_push_cannot_cross_boundary",!site.bounds().overlaps(owner.getX(),owner.getZ(),.3),owner.position());
        owner.gameMode.changeGameModeForPlayer(GameType.CREATIVE);
        check("creative_flight_cannot_enter",EstateRuntime.blocked(owner,level,inside().getX(),inside().getZ()),"creative");
        owner.moveTo(inside().getX()+.5,300,inside().getZ()+.5,0,0);EstateRuntime.enforce(owner);
        check("roof_login_fallback_ejects",!site.bounds().overlaps(owner.getX(),owner.getZ(),.3),owner.position());
        owner.moveTo(inside().getX()+.5,-40,inside().getZ()+.5,0,0);EstateRuntime.enforce(owner);
        check("underground_fallback_ejects",!site.bounds().overlaps(owner.getX(),owner.getZ(),.3),owner.position());
        owner.moveTo(inside().getX()+.5,inside().getY(),inside().getZ()+.5,0,0);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent(owner));
        check("login_event_ejects_unowned_player",!site.bounds().overlaps(owner.getX(),owner.getZ(),.3),owner.position());
        owner.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);outside(owner);outside(other);
        var bag=new ItemStack(ModItems.SPIRIT_STONE_BAG.get());
        SpiritStoneStorage.contents(bag,List.of(new ItemStack(ModItems.HIGH_GRADE_SPIRIT_STONE.get(),99)));
        owner.getInventory().setItem(1,bag);owner.getInventory().setItem(2,new ItemStack(ModItems.MEDIUM_GRADE_SPIRIT_STONE.get(),64));
        NpcDialogue.open(owner,npc);check("purchase_page_and_role",offered(page(owner),"estate_confirm")&&page(owner).role().equals("庄园管家"),page(owner));
        NpcDialogue.choose(owner,new NpcDialogueIntent(new StoryIntentPayload(npc.getId(),page(owner).nonce(),"estate_buy")));
        check("unoffered_confirm_cannot_buy",EstateSavedData.get(server).titles.get(site.key())==null,"forged option");
        stage=2;
    }
    private static void step(MinecraftServer server)throws Exception {
        switch(stage) {
            case 2 -> choose(owner,"estate_confirm");
            case 3 -> {
                choose(owner,"estate_buy");
                check("99_high_and_medium_insufficient",EstateSavedData.get(server).titles.get(site.key())==null,stones(owner,ModItems.HIGH_GRADE_SPIRIT_STONE.get()));
                check("failure_preserves_all_denominations",stones(owner,ModItems.HIGH_GRADE_SPIRIT_STONE.get()).intValueExact()==99&&stones(owner,ModItems.MEDIUM_GRADE_SPIRIT_STONE.get()).intValueExact()==64,"99 high, 64 medium");
                owner.getInventory().setItem(0,new ItemStack(ModItems.HIGH_GRADE_SPIRIT_STONE.get(),64));
                SpiritStoneStorage.contents(owner.getInventory().getItem(1),List.of(new ItemStack(ModItems.HIGH_GRADE_SPIRIT_STONE.get(),86)));
                other.getInventory().setItem(0,new ItemStack(ModItems.HIGH_GRADE_SPIRIT_STONE.get(),64));
                other.getInventory().setItem(1,new ItemStack(ModItems.HIGH_GRADE_SPIRIT_STONE.get(),36));
                NpcDialogue.open(other,npc);
            }
            case 4 -> { choose(owner,"estate_home");choose(other,"estate_confirm");staleOther=page(other); }
            case 5 -> choose(owner,"estate_confirm");
            case 6 -> {
                choose(owner,"estate_buy");
                check("100_exact_loose_and_bag_payment",stones(owner,ModItems.HIGH_GRADE_SPIRIT_STONE.get()).intValueExact()==50&&stones(owner,ModItems.MEDIUM_GRADE_SPIRIT_STONE.get()).intValueExact()==64,stones(owner,ModItems.HIGH_GRADE_SPIRIT_STONE.get()));
                check("owner_uuid_recorded",EstateSavedData.get(server).titles.get(site.key()).owner().equals(OWNER),EstateSavedData.get(server).titles.snapshot());
            }
            case 7 -> {
                NpcDialogue.choose(owner,paidIntent);
                NpcDialogue.choose(other,new NpcDialogueIntent(new StoryIntentPayload(npc.getId(),staleOther.nonce(),"estate_buy")));
                check("replay_and_racing_buyer_no_charge",stones(owner,ModItems.HIGH_GRADE_SPIRIT_STONE.get()).intValueExact()==50&&stones(other,ModItems.HIGH_GRADE_SPIRIT_STONE.get()).intValueExact()==100,"balances 50 / 100");
                check("owner_teleport_allowed",owner.teleportTo(server.overworld(),inside().getX()+.5,inside().getY(),inside().getZ()+.5,Set.of(),0,0),owner.position());
                EstateRuntime.enforce(owner);check("owner_can_remain_inside",site.bounds().overlaps(owner.getX(),owner.getZ(),.3),owner.position());
                check("other_still_blocked",EstateRuntime.blocked(other,server.overworld(),inside().getX(),inside().getZ()),"sold");
                var mount=EntityType.PIG.create(server.overworld());mount.moveTo(inside().getX()+.5,inside().getY(),inside().getZ()+.5,0,0);mount.setNoAi(true);server.overworld().addFreshEntity(mount);
                other.startRiding(mount,true);other.moveTo(inside().getX()+.5,inside().getY(),inside().getZ()+.5,0,0);EstateRuntime.enforce(other);
                check("mounted_intruder_dismounted_and_ejected",!other.isPassenger()&&!site.bounds().overlaps(other.getX(),other.getZ(),.3),other.position());mount.discard();
                server.getPlayerList().saveAll();EstateSavedData.get(server).setDirty();finish(server);return;
            }
            default -> throw new IllegalStateException("unknown estate probe stage "+stage);
        }
        stage++;
    }
    private static void restart(MinecraftServer server)throws Exception {
        check("title_survives_server_restart",EstateSavedData.get(server).titles.get(site.key()).owner().equals(OWNER),EstateSavedData.get(server).titles.snapshot());
        check("both_balances_survive_restart",stones(owner,ModItems.HIGH_GRADE_SPIRIT_STONE.get()).intValueExact()==50&&stones(other,ModItems.HIGH_GRADE_SPIRIT_STONE.get()).intValueExact()==100,"50 / 100");
        check("owner_reenters_after_restart",owner.teleportTo(server.overworld(),inside().getX()+.5,inside().getY(),inside().getZ()+.5,Set.of(),0,0),owner.position());
        check("other_cannot_teleport_after_restart",!other.teleportTo(server.overworld(),inside().getX()+.5,inside().getY(),inside().getZ()+.5,Set.of(),0,0),other.position());
        outside(owner);NpcDialogue.open(owner,npc);check("restart_sold_has_no_purchase_offer",!offered(page(owner),"estate_confirm")&&!offered(page(owner),"estate_buy"),page(owner));
        finish(server);
    }
    private static void finish(MinecraftServer server)throws Exception {
        for(long key:server.overworld().getForcedChunks().toLongArray()) { var p=new net.minecraft.world.level.ChunkPos(key);server.overworld().setChunkForced(p.x,p.z,false); }
        Files.createDirectories(OUT);Files.writeString(OUT.resolve(PHASE+".json"),new GsonBuilder().setPrettyPrinting().create().toJson(Map.of("phase",PHASE,"pass",failures.isEmpty(),"checks",checks,"failures",failures,"mapped_input","not_verified")));
        stop=server.getTickCount()+100;
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post e) {
        if(PHASE==null)return;var server=e.getServer();
        if(stop>=0) { if(server.getTickCount()>=stop)server.halt(false);return; }
        if(server.getTickCount()<80||server.getTickCount()<next)return;
        try { if(stage==0)begin(server);else if(stage==1)gate(server);else step(server);next=server.getTickCount()+20; }
        catch(Throwable error) { error.printStackTrace();failures.add(error.toString());try{finish(server);}catch(Exception nested){server.halt(false);} }
    }
}
