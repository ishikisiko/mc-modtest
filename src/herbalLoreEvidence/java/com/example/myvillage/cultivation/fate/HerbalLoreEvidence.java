package com.example.myvillage.cultivation.fate;

import com.example.myvillage.cultivation.*;
import com.example.myvillage.sim.runtime.WorldSimRuntime;
import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.*;
import net.minecraft.network.protocol.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.server.network.*;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** 独立服务器真实方块破坏与物品掉落实验，不代表映射玩家操作。 */
@EventBusSubscriber(modid="myvillage")
public final class HerbalLoreEvidence {
    private static final Path OUT=Path.of(System.getenv().getOrDefault("MYVILLAGE_HERBAL_OUT","../reports/herbal-native"));
    private static final BlockPos AT=new BlockPos(5,65,0);
    private static final List<Map<String,Object>> CHECKS=new ArrayList<>();
    private static final List<String> FAILURES=new ArrayList<>();
    private static ServerPlayer player;
    private static int stage,stopAt=-1,readyBy;
    private static boolean cancelBreak,cancelDrops;
    private static void check(String id,boolean pass,Object detail){
        CHECKS.add(Map.of("id",id,"pass",pass,"detail",String.valueOf(detail)));
        if(!pass)throw new IllegalStateException(id+": "+detail);
    }
    private static final class MemoryConnection extends ServerGamePacketListenerImpl {
        MemoryConnection(MinecraftServer s,ServerPlayer p){super(s,new Connection(PacketFlow.SERVERBOUND),p,CommonListenerCookie.createInitial(p.getGameProfile(),false));}
        @Override public boolean hasChannel(ResourceLocation c){return true;}
        @Override public void send(Packet<?> p){}
        @Override public void send(Packet<?> p,PacketSendListener l){}
    }
    @SuppressWarnings("unchecked") private static void prepare(MinecraftServer server)throws Exception{
        WorldSimRuntime.setPaused(true);var level=server.overworld();
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){level.setChunkForced(x,z,true);level.getChunk(x,z);}
        for(int x=-10;x<=10;x++)for(int z=-10;z<=10;z++){
            level.setBlock(new BlockPos(x,64,z),Blocks.DIRT.defaultBlockState(),3);
            for(int y=65;y<70;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3);
        }
        player=new ServerPlayer(server,level,new GameProfile(UUID.nameUUIDFromBytes("herbal-proof".getBytes()),"HerbalProof"),ClientInformation.createDefault());
        player.connection=new MemoryConnection(server,player);player.moveTo(.5,65,.5,0,0);
        var ids=PlayerList.class.getDeclaredField("playersByUUID");ids.setAccessible(true);((Map<UUID,ServerPlayer>)ids.get(server.getPlayerList())).put(player.getUUID(),player);
        var players=PlayerList.class.getDeclaredField("players");players.setAccessible(true);((List<ServerPlayer>)players.get(server.getPlayerList())).add(player);
        level.addNewPlayer(player);player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        check("profile_root",CultivationService.replaceProfile(player,CultivationProfile.defaultProfile().withSpiritualRoot(new SpiritualRoot(Map.of(ResourceLocation.parse("myvillage:wood"),60)))).success(),"木灵根60");
        readyBy=server.getTickCount()+200;stage=1;
    }
    private static void setTrait(boolean active){
        var picks=List.of("myvillage:"+(active?"herbal_lore":"bright"),"myvillage:robust","myvillage:lightfoot");
        CultivationService.replaceFates(player,FateState.empty().choice(picks,"",picks,true,1),false);
    }
    private static long materialEntities(ServerLevel level){
        long count=0;for(var entity:level.getAllEntities())if(entity instanceof ItemEntity item
                && HerbalLoreRuntime.materials().contains(BuiltInRegistries.ITEM.getKey(item.getItem().getItem()).toString())){
            check("bonus_item_count_one",item.getItem().getCount()==1,item.getItem());count++;
        }return count;
    }
    private static long breakBatch(ServerLevel level,BlockState state,int amount){
        clearDrops(level);
        level.setBlock(AT.below(),state.getBlock()==Blocks.WHEAT?Blocks.FARMLAND.defaultBlockState():Blocks.DIRT.defaultBlockState(),3);
        for(int i=0;i<amount;i++){level.setBlock(AT,state,2);player.gameMode.destroyBlock(AT);}
        return materialEntities(level);
    }
    private static void clearDrops(ServerLevel level){
        var items=new ArrayList<ItemEntity>();for(var entity:level.getAllEntities())if(entity instanceof ItemEntity item)items.add(item);
        items.forEach(ItemEntity::discard);
    }
    private static void run(MinecraftServer server)throws Exception{
        var level=server.overworld();
        if(!level.isPositionEntityTicking(AT)&&server.getTickCount()<readyBy)return;
        check("fixture_entity_ready",level.isPositionEntityTicking(AT),AT);
        var definition=FateService.definitions(player.registryAccess()).get("myvillage:herbal_lore");
        check("synced_fate_two_percent",definition!=null&&definition.tier()==0&&definition.effect("herbal_find_chance")==.02,definition);
        setTrait(false);check("no_trait_no_bonus",breakBatch(level,Blocks.SHORT_GRASS.defaultBlockState(),256)==0,"256次");
        setTrait(true);check("active_trait",FateService.effect(player,"herbal_find_chance")==.02,FateService.state(player));
        check("grass_actual_break_adds_material",breakBatch(level,Blocks.SHORT_GRASS.defaultBlockState(),1024)>0,"1024次实际破坏");
        check("unripe_crop_actual_break_adds_material",breakBatch(level,Blocks.WHEAT.defaultBlockState(),1024)>0,"1024次未成熟作物实际破坏");
        for(var block:List.of(Blocks.OAK_LOG,Blocks.OAK_LEAVES,Blocks.CACTUS,Blocks.SUGAR_CANE,Blocks.VINE,Blocks.MOSS_BLOCK,Blocks.CHORUS_FLOWER))
            check("tag_plant_"+BuiltInRegistries.BLOCK.getKey(block),HerbalLoreRuntime.plant(block.defaultBlockState()),block);
        check("stone_no_bonus",breakBatch(level,Blocks.STONE.defaultBlockState(),256)==0,"非植物");
        player.gameMode.changeGameModeForPlayer(GameType.CREATIVE);
        check("creative_no_bonus",breakBatch(level,Blocks.SHORT_GRASS.defaultBlockState(),256)==0,"创造");
        player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        cancelBreak=true;check("cancelled_break_no_bonus",breakBatch(level,Blocks.SHORT_GRASS.defaultBlockState(),256)==0,"取消破坏");cancelBreak=false;
        cancelDrops=true;check("cancelled_drops_no_bonus",breakBatch(level,Blocks.SHORT_GRASS.defaultBlockState(),256)==0,"取消掉落");cancelDrops=false;
        clearDrops(level);
        for(int i=0;i<256;i++){level.setBlock(AT,Blocks.SHORT_GRASS.defaultBlockState(),2);level.destroyBlock(AT,true);}
        check("unattributed_break_no_bonus",materialEntities(level)==0,"无玩家归因");
        finish(server);
    }
    @SubscribeEvent public static void protect(BlockEvent.BreakEvent e){if(cancelBreak&&e.getPos().equals(AT))e.setCanceled(true);}
    @SubscribeEvent public static void drops(BlockDropsEvent e){if(cancelDrops&&e.getPos().equals(AT))e.setCanceled(true);}
    private static void finish(MinecraftServer server)throws Exception{
        Files.createDirectories(OUT);Files.writeString(OUT.resolve("native.json"),new GsonBuilder().setPrettyPrinting().create().toJson(Map.of("pass",FAILURES.isEmpty(),"checks",CHECKS,"failures",FAILURES,"mapped_input","not_verified")));stopAt=server.getTickCount()+5;
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post e){
        if(System.getenv("MYVILLAGE_HERBAL_OUT")==null)return;var server=e.getServer();
        if(stopAt>=0){if(server.getTickCount()>=stopAt)server.halt(false);return;}
        if(server.getTickCount()<80)return;
        try{if(stage==0)prepare(server);else run(server);}catch(Throwable t){t.printStackTrace();FAILURES.add(t.toString());try{finish(server);}catch(Exception ignored){server.halt(false);}}
    }
}
