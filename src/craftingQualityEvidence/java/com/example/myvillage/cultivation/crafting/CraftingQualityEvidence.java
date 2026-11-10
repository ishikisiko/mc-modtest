package com.example.myvillage.cultivation.crafting;

import com.example.myvillage.alchemy.*;
import com.example.myvillage.alchemy.array.*;
import com.example.myvillage.block.ModBlocks;
import com.example.myvillage.block.entity.AlchemyFurnaceBlockEntity;
import com.example.myvillage.cultivation.*;
import com.example.myvillage.forging.*;
import com.example.myvillage.item.*;
import com.example.myvillage.sim.runtime.WorldSimRuntime;
import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** 专用服务器事务和真实磁盘重启探针。指定结果只用于覆盖分支，未映射玩家输入。 */
@EventBusSubscriber(modid="myvillage")
public final class CraftingQualityEvidence {
    private static final String PHASE=System.getenv("MYVILLAGE_CRAFTING_PHASE");
    private static final Path OUT=Path.of(System.getenv().getOrDefault("MYVILLAGE_CRAFTING_OUT","../reports/crafting-native"));
    private static final BlockPos FURNACE=new BlockPos(0,80,0),PENDING=new BlockPos(4,80,0),ARRAY=new BlockPos(10,80,0),PREP=new BlockPos(0,80,10),ASSEMBLY=new BlockPos(4,80,10);
    private static final List<Map<String,Object>> CHECKS=new ArrayList<>();
    private static final List<String> DONE=new ArrayList<>(),FAILURES=new ArrayList<>();
    private static final String QI="myvillage:qi_refining",RECIPE="forged_pickaxe";
    private static boolean finished;
    private static void check(String id,boolean pass,Object detail){CHECKS.add(Map.of("id",id,"pass",pass,"detail",String.valueOf(detail)));if(!pass)throw new IllegalStateException(id+": "+detail);}
    private static ItemStack item(String id,int count){return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)),count);}
    private static ItemStack fuel(int count){return new ItemStack(BuiltInRegistries.ITEM.getTagOrEmpty(SpiritStoneTags.LOW_GRADE_SPIRIT_STONE).iterator().next().value(),count);}
    private static ServerPlayer actor(MinecraftServer server){
        var p=FakePlayerFactory.get(server.overworld(),new GameProfile(UUID.nameUUIDFromBytes("crafting-quality-evidence".getBytes()),"CraftingProof"));
        p.setHealth(p.getMaxHealth());CultivationService.setSpiritualRoot(p,new SpiritualRoot(Map.of(ResourceLocation.parse("myvillage:metal"),100)));
        CultivationService.initializeCraftingAptitudes(p);CultivationService.setCraftingAptitude(p,CraftingAptitudes.Profession.ALCHEMY,60);CultivationService.setCraftingAptitude(p,CraftingAptitudes.Profession.FORGING,60);
        AlchemyKnowledge.learn(p,"huiling");return p;
    }
    private static void near(ServerPlayer p,BlockPos pos,AbstractContainerMenu menu){p.setPos(pos.getX()+.5,pos.getY(),pos.getZ()-.5);p.containerMenu=menu;}
    private static AlchemyFurnaceBlockEntity furnace(ServerLevel level,BlockPos pos){level.getChunkAt(pos);level.setBlockAndUpdate(pos,ModBlocks.ALCHEMY_FURNACE.get().defaultBlockState());return (AlchemyFurnaceBlockEntity)level.getBlockEntity(pos);}
    private static AlchemyMenu start(ServerPlayer p,AlchemyFurnaceBlockEntity be){
        be.clearContent();var r=AlchemyRules.get().recipe("huiling").orElseThrow();
        for(int i=0;i<r.ingredients().size();i++)be.setItem(i,item(r.ingredients().get(i),r.count(i)*2));be.setItem(3,fuel(r.fuelCount()*2));
        var menu=new AlchemyMenu(10,p.getInventory(),be,be.data);near(p,be.getBlockPos(),menu);
        check("furnace_select",menu.clickMenuButton(p,1),be.getBlockPos());
        check("furnace_start",menu.clickMenuButton(p,0)&&be.active(),be.data.get(2));
        return menu;
    }
    private static AlchemyBatch fixed(ServerPlayer p,double... gaussian){var r=AlchemyRules.get().recipe("huiling").orElseThrow();int[] i={0};return AlchemyBatch.create(p.getUUID(),r,60,()->gaussian[i[0]++]);}
    private static void force(AlchemyFurnaceBlockEntity be,AlchemyBatch batch){var tag=be.saveWithoutMetadata(be.getLevel().registryAccess());tag.put("Batch",batch.save());tag.putInt("Progress",0);be.loadWithComponents(tag,be.getLevel().registryAccess());check("forced_batch_valid",be.active(),tag);}
    private static void tick(AlchemyFurnaceBlockEntity be,int count){for(int i=0;i<count;i++)AlchemyFurnaceBlockEntity.serverTick(be.getLevel(),be.getBlockPos(),be.getBlockState(),be);}
    private static int outputs(AlchemyFurnaceBlockEntity be){int n=0;for(int i=4;i<8;i++)n+=be.getItem(i).getCount();return n;}
    private static void finishFurnace(AlchemyFurnaceBlockEntity be,int successes,int failures){
        tick(be,400);check("furnace_outcomes_"+successes+"_"+failures,!be.active()&&outputs(be)==successes&&be.data.get(7)==successes&&be.data.get(4)==failures,be.saveWithoutMetadata(be.getLevel().registryAccess()));
        for(int i:new int[]{0,1,3})check("furnace_cost_once_"+i,be.getItem(i).getCount()==1,be.getItem(i));
        tick(be,10);check("furnace_no_automatic_second_batch",!be.active()&&be.getItem(3).getCount()==1,be.getItem(3));
    }
    private static AlchemyArrayBlockEntity array(ServerLevel level){
        for(var c:AlchemyArrayRules.get().cells()){
            var pos=AlchemyArrayPattern.worldPos(ARRAY,Direction.NORTH,c);level.getChunkAt(pos);
            var state=c.role()==AlchemyArrayRules.Role.AIR?Blocks.AIR.defaultBlockState():AlchemyArrayContent.PARTS.get(c.role()).get().defaultBlockState().setValue(AlchemyArrayBlock.FACING,AlchemyArrayPattern.outward(Direction.NORTH,c.role()));
            level.setBlockAndUpdate(pos,state);
        }return (AlchemyArrayBlockEntity)level.getBlockEntity(ARRAY);
    }
    private static void arrayTick(AlchemyArrayBlockEntity be,int count){for(int i=0;i<count;i++)AlchemyArrayBlockEntity.serverTick(be.getLevel(),be.getBlockPos(),be.getBlockState(),be);}
    private static void fill(ServerPlayer p,Map<String,Integer> costs){
        p.getInventory().clearContent();for(var e:costs.entrySet()){
            var value=e.getKey().startsWith("#")?new ItemStack(BuiltInRegistries.ITEM.getTagOrEmpty(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM,ResourceLocation.parse(e.getKey().substring(1)))).iterator().next().value(),e.getValue()):item(e.getKey(),e.getValue());
            check("fixture_inventory_space",p.getInventory().add(value),e);
        }
    }
    private static void prepare(MinecraftServer server)throws Exception{
        WorldSimRuntime.setPaused(true);var level=server.overworld();var p=actor(server);var r=AlchemyRules.get().recipe("huiling").orElseThrow();
        var be=furnace(level,FURNACE);var menu=start(p,be);
        check("eight_machine_slots",menu.slots.size()==44,menu.slots.size());
        for(int i=4;i<8;i++)check("output_extract_only_"+i,!menu.slots.get(i).mayPlace(new ItemStack(Items.STONE)),i);
        check("active_inputs_locked",be.removeItem(0,1).isEmpty()&&!menu.slots.get(0).mayPickup(p),be.getItem(0));
        check("active_start_rejected",!menu.clickMenuButton(p,0),be.active());
        var other=new AlchemyMenu(11,p.getInventory(),be,be.data);p.containerMenu=other;
        check("foreign_menu_rejected",!menu.clickMenuButton(p,0),p.containerMenu);p.containerMenu=menu;
        DONE.add("furnace_authority_and_slots");
        force(be,fixed(p,-2,3));finishFurnace(be,2,0);
        check("mixed_quality_components",PillQuality.get(be.getItem(4))==1&&PillQuality.get(be.getItem(5))==4,be.getItem(4)+" / "+be.getItem(5));
        start(p,be);force(be,fixed(p,-4,0));finishFurnace(be,1,1);
        start(p,be);force(be,fixed(p,-4,-4));finishFurnace(be,0,2);check("whole_failure_status",be.data.get(2)==7,be.data.get(2));
        DONE.add("furnace_mixed_partial_and_whole_failure");
        var pending=furnace(level,PENDING);start(p,pending);force(pending,fixed(p,-2,3));
        for(int i=4;i<8;i++)pending.setItem(i,new ItemStack(Items.STONE,64));
        String batch=pending.saveWithoutMetadata(level.registryAccess()).getCompound("Batch").toString();
        tick(pending,20);check("blocked_furnace_keeps_batch",pending.active()&&pending.data.get(0)==0&&batch.equals(pending.saveWithoutMetadata(level.registryAccess()).getCompound("Batch").toString()),batch);
        var ar=array(level);for(int i=0;i<2;i++)ar.setItem(i,item(r.ingredients().get(i),2));ar.setItem(2,fuel(2));
        check("array_structure_formed",ar.freshCheck().formed(),ar.freshCheck());
        var am=new AlchemyArrayMenu(20,p.getInventory(),ar,ar.data);near(p,ARRAY,am);
        check("array_selected",am.clickMenuButton(p,2),ar.selectedRecipe());check("array_enabled",am.clickMenuButton(p,0)&&ar.active(),ar.status());
        var at=ar.saveWithoutMetadata(level.registryAccess());at.put("Batch",fixed(p,-2,3).save());at.putBoolean("Enabled",false);at.putInt("Progress",0);ar.loadWithComponents(at,level.registryAccess());
        check("array_restored_valid_batch",ar.active(),at);
        var missing=AlchemyArrayRules.get().cells().stream().filter(c->c.role()==AlchemyArrayRules.Role.CASING).findFirst().orElseThrow();
        var mp=AlchemyArrayPattern.worldPos(ARRAY,Direction.NORTH,missing);var ms=level.getBlockState(mp);level.setBlockAndUpdate(mp,Blocks.AIR.defaultBlockState());arrayTick(ar,4);
        check("broken_structure_pauses",ar.active()&&!ar.formed()&&ar.progress()==0,ar.status());level.setBlockAndUpdate(mp,ms);
        for(int i=3;i<7;i++)ar.setItem(i,new ItemStack(Items.STONE,64));arrayTick(ar,10);check("blocked_array_keeps_batch",ar.active()&&ar.progress()==0,ar.status());
        DONE.add("array_structure_and_output_pause");
        level.setBlockAndUpdate(PREP,ModBlocks.FORGING_TABLE.get().defaultBlockState());var prep=(PreparationBlockEntity)level.getBlockEntity(PREP);
        int index=0;for(int i=0;i<WorkshopRules.get().crafts().size();i++)if(WorkshopRules.get().crafts().get(i).recipe().equals(RECIPE))index=i;
        var pm=new FoundryMenu(30,p.getInventory(),prep,prep.data,FoundryMenu.Kind.PREPARATION);near(p,PREP,pm);
        check("automatic_recipe",pm.clickMenuButton(p,20+index),index);
        var blank=WorkshopItems.blank(new Workpiece(RECIPE,0,0,0));fill(p,PreparationBlockEntity.automaticCosts(blank.get(ModDataComponents.WORKPIECE.get()),true));
        check("automatic_started",pm.clickMenuButton(p,1)&&prep.active(),prep.data.get(3));
        var pt=prep.saveWithoutMetadata(level.registryAccess());pt.putInt("Quality",0);pt.remove("Frozen");prep.loadWithComponents(pt,level.registryAccess());
        check("automatic_failure_is_active",prep.active()&&prep.data.get(3)!=9,prep.data.get(3));prep.setItem(2,new ItemStack(Items.STONE));prep.serverTick();
        check("automatic_failure_output_pause",prep.active()&&prep.data.get(0)==index&&prep.data.get(6)==0,prep.data.get(3));
        level.setBlockAndUpdate(ASSEMBLY,ModBlocks.ASSEMBLY_TABLE.get().defaultBlockState());var assembly=(AssemblyBlockEntity)level.getBlockEntity(ASSEMBLY);
        assembly.setItem(0,WorkshopItems.blank(new Workpiece(RECIPE,4,WorkshopRules.get().formingPoints(RECIPE),1)));assembly.setItem(1,new ItemStack(ModItems.FOUNDRY_ITEMS.get("wood_handle").get(),2));
        var asm=new FoundryMenu(40,p.getInventory(),assembly,assembly.data,FoundryMenu.Kind.ASSEMBLY);near(p,ASSEMBLY,asm);check("assembly_started",asm.clickMenuButton(p,0),assembly.data.get(3));
        var st=assembly.saveWithoutMetadata(level.registryAccess());st.putInt("Quality",0);st.remove("Frozen");assembly.loadWithComponents(st,level.registryAccess());
        check("assembly_failure_is_active",assembly.active()&&assembly.data.get(3)!=7,assembly.data.get(3));
        for(int i=0;i<FoundryRules.get().assembly().ticks();i++)assembly.serverTick();
        check("assembly_failure_consumes_once",!assembly.active()&&assembly.getItem(0).isEmpty()&&assembly.getItem(1).getCount()==1&&assembly.getItem(2).isEmpty(),assembly.saveWithoutMetadata(level.registryAccess()));
        DONE.add("assembly_failed_job");
        assembly.setItem(0,WorkshopItems.blank(new Workpiece(RECIPE,4,WorkshopRules.get().formingPoints(RECIPE),1)));
        check("assembly_second_started",asm.clickMenuButton(p,0),assembly.data.get(3));var ast=assembly.saveWithoutMetadata(level.registryAccess());
        ast.putInt("Quality",4);var perfect=AssemblyPlan.prepare(assembly.getItem(0),assembly.getItem(1),4).result();ast.put("Frozen",perfect.save(level.registryAccess()));assembly.loadWithComponents(ast,level.registryAccess());assembly.setItem(2,new ItemStack(Items.STONE));
        check("infusion_preserves_perfect",EquipmentInfusion.prepare(perfect,"tough",1).result().get(ModDataComponents.FORGE_MARK.get()).quality()==4,perfect);
        var replacement=AssemblyPlan.prepare(perfect,new ItemStack(ModItems.FOUNDRY_ITEMS.get("bamboo_handle").get()),1);
        check("handle_preserves_perfect",replacement.valid()&&replacement.result().get(ModDataComponents.FORGE_MARK.get()).quality()==4,replacement);
        DONE.add("finished_quality_preserved");
        Files.createDirectories(OUT);Files.writeString(OUT.resolve("frozen.json"),new GsonBuilder().setPrettyPrinting().create().toJson(Map.of("furnace",batch,"array",ar.saveWithoutMetadata(level.registryAccess()).getCompound("Batch").toString(),"assembly",perfect.save(level.registryAccess()).toString(),"preparation",prep.saveWithoutMetadata(level.registryAccess()).getList("Reserved",Tag.TAG_COMPOUND).toString())));
        p.containerMenu=p.inventoryMenu;server.saveEverything(false,true,true);DONE.add("disk_checkpoint_written");
    }
    private static void restart(MinecraftServer server)throws Exception{
        WorldSimRuntime.setPaused(true);var level=server.overworld();var saved=JsonParser.parseString(Files.readString(OUT.resolve("frozen.json"))).getAsJsonObject();
        for(var pos:List.of(PENDING,ARRAY,PREP,ASSEMBLY))level.getChunkAt(pos);
        var f=(AlchemyFurnaceBlockEntity)level.getBlockEntity(PENDING);check("disk_furnace_loaded",f!=null&&f.active(),f);
        check("disk_furnace_same_outcomes",f.saveWithoutMetadata(level.registryAccess()).getCompound("Batch").toString().equals(saved.get("furnace").getAsString()),f.saveWithoutMetadata(level.registryAccess()));
        for(int i=4;i<8;i++)f.setItem(i,ItemStack.EMPTY);finishFurnace(f,2,0);DONE.add("furnace_disk_restart");
        var ar=(AlchemyArrayBlockEntity)level.getBlockEntity(ARRAY);check("disk_array_same_outcomes",ar!=null&&ar.active()&&ar.saveWithoutMetadata(level.registryAccess()).getCompound("Batch").toString().equals(saved.get("array").getAsString()),ar);
        for(int i=3;i<7;i++)ar.setItem(i,ItemStack.EMPTY);arrayTick(ar,400);
        check("disk_array_completion",!ar.active()&&ar.getItem(3).getCount()==1&&PillQuality.get(ar.getItem(3))==1&&ar.getItem(4).getCount()==1&&PillQuality.get(ar.getItem(4))==4,ar.saveWithoutMetadata(level.registryAccess()));
        arrayTick(ar,10);check("disk_array_cost_once",ar.getItem(0).getCount()==1&&ar.getItem(1).getCount()==1&&ar.getItem(2).getCount()==1,ar.saveWithoutMetadata(level.registryAccess()));DONE.add("array_disk_restart");
        var prep=(PreparationBlockEntity)level.getBlockEntity(PREP);check("disk_automatic_failure_loaded",prep!=null&&prep.active()&&prep.saveWithoutMetadata(level.registryAccess()).getInt("Quality")==0&&prep.data.get(3)!=9,prep);
        check("disk_automatic_same_escrow",prep.saveWithoutMetadata(level.registryAccess()).getList("Reserved",Tag.TAG_COMPOUND).toString().equals(saved.get("preparation").getAsString()),prep.saveWithoutMetadata(level.registryAccess()));
        prep.setItem(2,ItemStack.EMPTY);for(int i=0;i<30001&&prep.active();i++)prep.serverTick();
        check("disk_automatic_failure_completed",!prep.active()&&prep.getItem(0).isEmpty()&&prep.getItem(2).isEmpty()&&prep.saveWithoutMetadata(level.registryAccess()).getList("Reserved",Tag.TAG_COMPOUND).isEmpty()&&prep.getItem(3).is(Items.BUCKET),prep.saveWithoutMetadata(level.registryAccess()));DONE.add("automatic_failure_disk_restart");
        var assembly=(AssemblyBlockEntity)level.getBlockEntity(ASSEMBLY);check("disk_assembly_loaded",assembly!=null&&assembly.active()&&assembly.data.get(3)!=7,assembly);
        assembly.setItem(2,ItemStack.EMPTY);for(int i=0;i<FoundryRules.get().assembly().ticks();i++)assembly.serverTick();
        check("disk_assembly_same_result",!assembly.active()&&assembly.getItem(0).isEmpty()&&assembly.getItem(1).isEmpty()&&assembly.getItem(2).save(level.registryAccess()).toString().equals(saved.get("assembly").getAsString()),assembly.saveWithoutMetadata(level.registryAccess()));DONE.add("assembly_disk_restart");
    }
    private static void finish(MinecraftServer server){
        finished=true;try{Files.createDirectories(OUT);Files.writeString(OUT.resolve(PHASE+".json"),new GsonBuilder().setPrettyPrinting().create().toJson(Map.of("phase",PHASE,"pass",FAILURES.isEmpty(),"completed",DONE,"checks",CHECKS,"failures",FAILURES,"mapped_input","not_verified","time_accelerated",true)));}catch(Exception e){e.printStackTrace();}server.halt(false);
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event){
        if(PHASE==null||finished||event.getServer().getTickCount()<60)return;
        try{if(PHASE.equals("restart"))restart(event.getServer());else prepare(event.getServer());}catch(Throwable t){t.printStackTrace();FAILURES.add(t.toString());}finish(event.getServer());
    }
}
