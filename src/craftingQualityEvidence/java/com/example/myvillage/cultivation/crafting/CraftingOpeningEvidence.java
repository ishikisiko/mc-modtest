package com.example.myvillage.cultivation.crafting;

import com.example.myvillage.alchemy.*;
import com.example.myvillage.alchemy.array.*;
import com.example.myvillage.block.ModBlocks;
import com.example.myvillage.block.entity.AlchemyFurnaceBlockEntity;
import com.example.myvillage.cultivation.*;
import com.example.myvillage.cultivation.fate.*;
import com.example.myvillage.forging.ForgingCompletion;
import com.example.myvillage.item.SpiritStoneTags;
import com.example.myvillage.sim.runtime.WorldSimRuntime;
import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.network.*;
import net.minecraft.network.protocol.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.server.network.*;
import net.minecraft.server.players.PlayerList;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** 真实玩家附件与设备存盘探针；直接调用服务器意图，不作为映射输入验收。 */
@EventBusSubscriber(modid="myvillage")
public final class CraftingOpeningEvidence {
    private static final String PHASE=System.getenv("MYVILLAGE_OPENING_PHASE");
    private static final Path OUT=Path.of(System.getenv().getOrDefault("MYVILLAGE_OPENING_OUT","../reports/opening-native"));
    private static final UUID ID=UUID.fromString("b84712b1-dc01-4ced-8b74-e8a12be9a001");
    private static final BlockPos FURNACE=new BlockPos(0,80,0),ARRAY=new BlockPos(10,80,0);
    private static final List<String> TALENTS=List.of("myvillage:alchemy_prodigy","myvillage:forging_prodigy","myvillage:clod");
    private static final List<Map<String,Object>> CHECKS=new ArrayList<>();
    private static final List<String> DONE=new ArrayList<>(),FAILURES=new ArrayList<>();
    private static boolean finished;
    private static void check(String id,boolean pass,Object detail){CHECKS.add(Map.of("id",id,"pass",pass,"detail",String.valueOf(detail)));if(!pass)throw new IllegalStateException(id+": "+detail);}
    private static final class MemoryConnection extends ServerGamePacketListenerImpl {
        MemoryConnection(MinecraftServer s,ServerPlayer p){super(s,new Connection(PacketFlow.SERVERBOUND),p,CommonListenerCookie.createInitial(p.getGameProfile(),false));}
        @Override public boolean hasChannel(ResourceLocation c){return true;}
        @Override public void send(Packet<?> p){}
        @Override public void send(Packet<?> p,PacketSendListener l){}
    }
    @SuppressWarnings("unchecked") private static ServerPlayer actor(MinecraftServer server,boolean load)throws Exception{
        var p=new ServerPlayer(server,server.overworld(),new GameProfile(ID,"OpeningProof"),ClientInformation.createDefault());p.connection=new MemoryConnection(server,p);
        if(load)check("real_player_file_loaded",server.getPlayerList().load(p).isPresent(),ID);
        var ids=PlayerList.class.getDeclaredField("playersByUUID");ids.setAccessible(true);((Map<UUID,ServerPlayer>)ids.get(server.getPlayerList())).put(ID,p);
        var ps=PlayerList.class.getDeclaredField("players");ps.setAccessible(true);((List<ServerPlayer>)ps.get(server.getPlayerList())).add(p);
        p.moveTo(.5,80,.5,0,0);p.serverLevel().addNewPlayer(p);return p;
    }
    private static void near(ServerPlayer p,BlockPos pos,AbstractContainerMenu menu){p.setPos(pos.getX()+.5,pos.getY(),pos.getZ()-.5);p.containerMenu=menu;}
    private static ItemStack item(String id,int n){return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)),n);}
    private static ItemStack fuel(int n){return new ItemStack(BuiltInRegistries.ITEM.getTagOrEmpty(SpiritStoneTags.LOW_GRADE_SPIRIT_STONE).iterator().next().value(),n);}
    private static AlchemyBatch expected(ServerPlayer p,long seed){
        var random=RandomSource.create(seed);return AlchemyBatch.create(p.getUUID(),AlchemyRules.get().recipe("huiling").orElseThrow(),60,
                new CraftingQualityRules.Modifier(-5,4),random::nextGaussian);
    }
    private static CompoundTag tag(net.minecraft.world.level.block.entity.BlockEntity be){return be.saveWithoutMetadata(be.getLevel().registryAccess());}
    private static AlchemyArrayBlockEntity array(ServerLevel level){
        for(var c:AlchemyArrayRules.get().cells()){
            var pos=AlchemyArrayPattern.worldPos(ARRAY,Direction.NORTH,c);level.getChunkAt(pos);
            var state=c.role()==AlchemyArrayRules.Role.AIR?Blocks.AIR.defaultBlockState():AlchemyArrayContent.PARTS.get(c.role()).get().defaultBlockState().setValue(AlchemyArrayBlock.FACING,AlchemyArrayPattern.outward(Direction.NORTH,c.role()));
            level.setBlockAndUpdate(pos,state);
        }return (AlchemyArrayBlockEntity)level.getBlockEntity(ARRAY);
    }
    private static void arrayTick(AlchemyArrayBlockEntity be,int count){for(int i=0;i<count;i++)AlchemyArrayBlockEntity.serverTick(be.getLevel(),be.getBlockPos(),be.getBlockState(),be);}
    private static void prepare(MinecraftServer server)throws Exception{
        WorldSimRuntime.setPaused(true);var level=server.overworld();
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){level.setChunkForced(x,z,true);level.getChunk(x,z);}
        var p=actor(server,false);CultivationService.setSpiritualRoot(p,new SpiritualRoot(Map.of(ResourceLocation.parse("myvillage:metal"),100)));
        CultivationService.initializeCraftingAptitudes(p);check("awakened_before_fate_confirmation_stays_pending",!CultivationService.craftingAptitudes(p).initialized(),CultivationService.craftingAptitudes(p));
        check("pending_fates_do_not_apply_clod",CraftingTalents.modifier(p).equals(CraftingQualityRules.Modifier.NORMAL),CraftingTalents.modifier(p));
        var candidates=new ArrayList<>(TALENTS);candidates.addAll(List.of("myvillage:robust","myvillage:lightfoot","myvillage:bright"));
        CultivationService.replaceFates(p,FateState.empty().choice(candidates,"",List.of(),false,1),false);
        var stele=new BlockPos(1,80,0);level.setBlockAndUpdate(stele,ModBlocks.SPIRIT_TESTING_STELE.get().defaultBlockState());
        var token=SteleAwakeningSessions.open(p,stele);
        check("actual_fate_confirmation",FateService.intent(p,2,FateService.state(p).revision(),7,token),FateService.state(p));
        var apt=CultivationService.craftingAptitudes(p);check("both_geniuses_initialize_to_sixty",apt.initialized()&&apt.alchemy()==60&&apt.forging()==60,apt);
        check("selected_clod_runtime_modifier",CraftingTalents.modifier(p).equals(new CraftingQualityRules.Modifier(-5,4)),CraftingTalents.modifier(p));
        DONE.add("opening_confirmation_and_talents");
        var recipe=AlchemyRules.get().recipe("huiling").orElseThrow();AlchemyKnowledge.learn(p,recipe.id());
        level.setBlockAndUpdate(FURNACE,ModBlocks.ALCHEMY_FURNACE.get().defaultBlockState());var f=(AlchemyFurnaceBlockEntity)level.getBlockEntity(FURNACE);
        for(int i=0;i<recipe.ingredients().size();i++)f.setItem(i,item(recipe.ingredients().get(i),recipe.count(i)));f.setItem(3,fuel(recipe.fuelCount()));
        var fm=new AlchemyMenu(10,p.getInventory(),f,f.data);near(p,FURNACE,fm);check("furnace_select",fm.clickMenuButton(p,1),recipe.id());
        long seed=178231;var oracle=expected(p,seed);check("fixture_has_successful_pill",oracle.successfulCount()>0,oracle);
        level.random.setSeed(seed);check("furnace_starts_with_clod",fm.clickMenuButton(p,0),tag(f));
        check("furnace_uses_modified_independent_draws",AlchemyBatch.read(tag(f).getCompound("Batch")).orElseThrow().equals(oracle),tag(f));
        for(int i=4;i<8;i++)f.setItem(i,new ItemStack(Items.STONE,64));
        int changed=0;for(int i=0;i<100;i++){
            long s=5000+i;var random=RandomSource.create(s);double z=random.nextGaussian();
            int target=CraftingQualityRules.get().roll(60,"myvillage:qi_refining",0,new CraftingQualityRules.Modifier(-5,4),()->z).quality();
            if(target!=CraftingQualityRules.get().roll(60,"myvillage:qi_refining",0,()->z).quality())changed++;
            p.getRandom().setSeed(s);check("forging_clod_draw_"+i,ForgingCompletion.roll(p,"forged_pickaxe")==target,target);
        }check("forging_fixture_detects_missing_modifier",changed>0,changed);DONE.add("furnace_and_forging_production_draws");
        var ar=array(level);for(int i=0;i<recipe.ingredients().size();i++)ar.setItem(i==2?7:i,item(recipe.ingredients().get(i),recipe.count(i)*2));ar.setItem(2,fuel(recipe.fuelCount()*2));
        var am=new AlchemyArrayMenu(20,p.getInventory(),ar,ar.data);near(p,ARRAY,am);check("array_select",am.clickMenuButton(p,2),ar.selectedRecipe());
        level.random.setSeed(seed);check("array_authorized",am.clickMenuButton(p,0)&&ar.active(),tag(ar));
        check("array_uses_clod",AlchemyBatch.read(tag(ar).getCompound("Batch")).orElseThrow().equals(oracle),tag(ar));
        for(int i=3;i<7;i++)ar.setItem(i,new ItemStack(Items.STONE,64));arrayTick(ar,3);
        check("array_captures_modifier",tag(ar).getInt("Version")==3&&tag(ar).getDouble("MeanShift")==-5&&tag(ar).getDouble("VarianceMultiplier")==4&&tag(ar).getInt("Score")==60,tag(ar));
        check("growth_accepted",CultivationService.setCraftingAptitude(p,CraftingAptitudes.Profession.ALCHEMY,71).success()&&CultivationService.setCraftingAptitude(p,CraftingAptitudes.Profession.FORGING,67).success(),CultivationService.craftingAptitudes(p));
        CultivationService.initializeCraftingAptitudes(p);CultivationService.setSpiritualRoot(p,new SpiritualRoot(Map.of(ResourceLocation.parse("myvillage:wood"),70)));
        check("genius_does_not_reset_growth",CultivationService.craftingAptitudes(p).alchemy()==71&&CultivationService.craftingAptitudes(p).forging()==67,CultivationService.craftingAptitudes(p));
        p.setPos(100,80,100);p.containerMenu=p.inventoryMenu;
        Files.createDirectories(OUT);Files.writeString(OUT.resolve("frozen.json"),new Gson().toJson(Map.of("furnace",tag(f).getCompound("Batch").toString(),"array",tag(ar).getCompound("Batch").toString(),"talisman",apt.talisman())));
        server.getPlayerList().saveAll();server.saveEverything(false,true,true);DONE.add("player_and_array_disk_checkpoint");
    }
    private static void restart(MinecraftServer server)throws Exception{
        WorldSimRuntime.setPaused(true);var p=actor(server,true);p.setPos(100,80,100);var level=server.overworld();var saved=JsonParser.parseString(Files.readString(OUT.resolve("frozen.json"))).getAsJsonObject();
        var apt=CultivationService.craftingAptitudes(p);check("grown_scores_survive_restart",apt.alchemy()==71&&apt.forging()==67&&apt.talisman()==saved.get("talisman").getAsInt(),apt);
        check("confirmed_fates_survive_restart",FateService.state(p).confirmed()&&new HashSet<>(FateService.state(p).selected()).equals(Set.copyOf(TALENTS)),FateService.state(p));
        check("clod_survives_restart",CraftingTalents.modifier(p).equals(new CraftingQualityRules.Modifier(-5,4)),CraftingTalents.modifier(p));CultivationService.initializeCraftingAptitudes(p);
        check("reinitialization_preserves_growth",CultivationService.craftingAptitudes(p).equals(apt),CultivationService.craftingAptitudes(p));DONE.add("real_player_save_restart");
        level.getChunkAt(FURNACE);level.getChunkAt(ARRAY);var f=(AlchemyFurnaceBlockEntity)level.getBlockEntity(FURNACE);var ar=(AlchemyArrayBlockEntity)level.getBlockEntity(ARRAY);
        check("furnace_frozen_clod_outcomes_survive",f!=null&&f.active()&&tag(f).getCompound("Batch").toString().equals(saved.get("furnace").getAsString()),f);
        check("array_frozen_clod_outcomes_survive",ar!=null&&ar.active()&&tag(ar).getCompound("Batch").toString().equals(saved.get("array").getAsString()),ar);
        check("array_authorization_survives",tag(ar).getBoolean("Enabled")&&tag(ar).getInt("Score")==60&&tag(ar).getDouble("MeanShift")==-5&&tag(ar).getDouble("VarianceMultiplier")==4,tag(ar));
        var valid=tag(ar);var invalid=valid.copy();invalid.putDouble("VarianceMultiplier",0);ar.loadWithComponents(invalid,level.registryAccess());
        check("invalid_modifier_disables_authorization",!ar.active()&&!tag(ar).getBoolean("Enabled"),tag(ar));ar.loadWithComponents(valid,level.registryAccess());
        for(int i=3;i<7;i++)ar.setItem(i,ItemStack.EMPTY);int ticks=AlchemyRules.get().recipe("huiling").orElseThrow().durationTicks();arrayTick(ar,ticks);
        check("first_array_batch_settles_once",!ar.active()&&ar.getItem(0).getCount()==1&&ar.getItem(1).getCount()==1&&ar.getItem(2).getCount()==1,tag(ar));
        long seed=331233;level.random.setSeed(seed);arrayTick(ar,1);
        check("continued_batch_uses_saved_aptitude_and_clod",ar.active()&&AlchemyBatch.read(tag(ar).getCompound("Batch")).orElseThrow().equals(expected(p,seed)),tag(ar));DONE.add("array_modifier_save_restart_and_continuation");
    }
    private static void finish(MinecraftServer server){
        finished=true;try{Files.createDirectories(OUT);Files.writeString(OUT.resolve(PHASE+".json"),new GsonBuilder().setPrettyPrinting().create().toJson(Map.of("phase",PHASE,"pass",FAILURES.isEmpty(),"completed",DONE,"checks",CHECKS,"failures",FAILURES,"mapped_input","not_verified")));}catch(Exception e){e.printStackTrace();}server.halt(false);
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post e){if(PHASE==null||finished||e.getServer().getTickCount()<60)return;
        try{if(PHASE.equals("restart"))restart(e.getServer());else prepare(e.getServer());}catch(Throwable t){t.printStackTrace();FAILURES.add(t.toString());}finish(e.getServer());}
}
