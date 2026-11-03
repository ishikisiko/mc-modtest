package com.example.myvillage.forging;

import com.example.myvillage.block.ModBlocks;
import com.example.myvillage.block.entity.ForgingAnvilBlockEntity;
import com.example.myvillage.cultivation.CultivationService;
import com.example.myvillage.cultivation.SpiritualRoot;
import com.example.myvillage.cultivation.crafting.CraftingAptitudes;
import com.example.myvillage.forging.array.*;
import com.example.myvillage.item.ModDataComponents;
import com.example.myvillage.item.QualityDisplay;
import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Isolated native fixtures: actual menu assembly and disk restart, not mapped player input. */
@EventBusSubscriber(modid="myvillage")
public final class ForgingQualityEvidence {
    private static final boolean ENABLED="1".equals(System.getenv("MYVILLAGE_FORGING_QUALITY_EVIDENCE"));
    private static final Path OUT=Path.of("../reports/forging-quality-native");
    private static final boolean RESTART=Files.exists(OUT.resolve("restart-fixture.json"));
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private static final BlockPos ANVIL=new BlockPos(0,80,0),CHEST=new BlockPos(2,80,0);
    private static final BlockPos ARRAY=new BlockPos(10,80,10);
    private record Trial(String recipe,int score,int quality) {String id(){return recipe+"_quality_"+quality+(score<0?"_unawakened":"");}}
    private static final List<Trial> TRIALS=new ArrayList<>();
    private static final List<Map<String,Object>> CHECKS=new ArrayList<>();
    private static final List<String> DONE=new ArrayList<>();
    private static final JsonArray SAVED=new JsonArray();
    private static int ticks,index,started;
    private static boolean finished;
    private static WorkshopMenu menu;
    private static ServerPlayer player;
    static {
        int[] scores={0,40,70,90};
        for(String recipe:List.of("qingfeng_sword","forged_pickaxe","xuantie_chestplate"))
            for(int q=1;q<=4;q++)TRIALS.add(new Trial(recipe,scores[q-1],q));
        TRIALS.add(new Trial("forged_pickaxe",-1,1));
    }
    private static void check(String name,boolean passed,Object detail){
        CHECKS.add(Map.of("name",name,"passed",passed,"detail",String.valueOf(detail)));
        if(!passed)throw new IllegalStateException(name+": "+detail);
    }
    private static ServerPlayer player(MinecraftServer server,String name,int score){
        var p=FakePlayerFactory.get(server.overworld(),new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)),name));
        p.setPos(.5,81,.5);p.setHealth(p.getMaxHealth());
        if(score>=0){
            CultivationService.setSpiritualRoot(p,new SpiritualRoot(Map.of(ResourceLocation.parse("myvillage:metal"),10000)));
            CultivationService.initializeCraftingAptitudes(p);
            CultivationService.setCraftingAptitude(p,CraftingAptitudes.Profession.FORGING,score);
        }
        return p;
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event){
        if(!ENABLED||finished||++ticks<40)return;
        var server=event.getServer();
        try{
            Files.createDirectories(OUT);
            var level=server.overworld();level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);
            level.getChunkAt(ANVIL);
            if(RESTART){restart(server);finish(server,null);return;}
            if(index==0&&menu==null){
                level.setBlockAndUpdate(ANVIL.below(),Blocks.STONE.defaultBlockState());
                level.setBlockAndUpdate(CHEST.below(),Blocks.STONE.defaultBlockState());
                level.setBlockAndUpdate(ANVIL,ModBlocks.FORGING_ANVIL.get().defaultBlockState());
                level.setBlockAndUpdate(CHEST,Blocks.CHEST.defaultBlockState());
            }
            if(index<TRIALS.size()){
                var trial=TRIALS.get(index);
                if(menu==null){
                    player=player(server,"ForgeQuality"+index,trial.score());
                    check(trial.id()+"_operator",ForgingCompletion.quality(player)==trial.quality(),EquipmentInfusion.qualityCap(player));
                    menu=new WorkshopMenu(10+index,player.getInventory(),ContainerLevelAccess.create(level,ANVIL));player.containerMenu=menu;
                    var anvil=(ForgingAnvilBlockEntity)level.getBlockEntity(ANVIL);
                    anvil.contents().setItem(0,WorkshopItems.blank(new Workpiece(trial.recipe(),4,WorkshopRules.get().forming_strikes(),2)));
                    anvil.contents().setItem(1,ItemStack.EMPTY);menu.broadcastChanges();
                    check(trial.id()+"_accepted",menu.clickMenuButton(player,WorkshopMenu.ASSEMBLE),menu.reason(WorkshopMenu.ASSEMBLE));
                    started=ticks;return;
                }
                menu.broadcastChanges();
                if(menu.output().isEmpty()){check(trial.id()+"_bounded",ticks-started<200,menu.remaining());return;}
                ItemStack result=menu.output();
                check(trial.id()+"_completed",result.getCount()==1&&ForgeMark.uninfused(trial.quality()).equals(result.get(ModDataComponents.FORGE_MARK.get())),result);
                check(trial.id()+"_display",QualityDisplay.quality(result)==trial.quality(),QualityDisplay.quality(result));
                var lines=new ArrayList<Component>();result.getItem().appendHoverText(result,Item.TooltipContext.of(level),lines,TooltipFlag.NORMAL);
                check(trial.id()+"_tooltip",lines.stream().noneMatch(x->x.getString().equals("forging.myvillage.trait.")),lines);
                result.set(DataComponents.CUSTOM_NAME,Component.literal("品质留存 "+index));result.setDamageValue(3);
                store(server,index,result);
                ((ForgingAnvilBlockEntity)level.getBlockEntity(ANVIL)).contents().setItem(1,ItemStack.EMPTY);
                menu.removed(player);player.containerMenu=player.inventoryMenu;menu=null;DONE.add(trial.id());index++;return;
            }
            var crafted=new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.parse("myvillage:qingfeng_sword")));
            player=player(server,"ForgeRecipe",90);
            NeoForge.EVENT_BUS.post(new PlayerEvent.ItemCraftedEvent(player,crafted,new SimpleContainer(9)));
            check("craft_event_quality",ForgeMark.uninfused(4).equals(crafted.get(ModDataComponents.FORGE_MARK.get())),crafted);
            DONE.add("crafting_event");
            var prepared=EquipmentInfusion.prepare(crafted,"channeling",1);
            check("low_operator_preserves_perfect",prepared.valid()&&new ForgeMark("channeling",4).equals(prepared.result().get(ModDataComponents.FORGE_MARK.get())),prepared);
            check("reinfusion_rejected",!EquipmentInfusion.prepare(prepared.result(),"tough",4).valid(),prepared.result());
            store(server,TRIALS.size(),prepared.result());DONE.add("infusion_quality");
            startArray(server);DONE.add("array_pending_quality");
            server.saveEverything(false,true,true);Files.writeString(OUT.resolve("restart-fixture.json"),JSON.toJson(SAVED));DONE.add("save_fixture");
            finish(server,null);
        }catch(Throwable failure){finish(server,failure);}
    }
    private static void store(MinecraftServer server,int slot,ItemStack item){
        var chest=(ChestBlockEntity)server.overworld().getBlockEntity(CHEST);chest.setItem(slot,item);chest.setChanged();
        var row=new JsonObject();row.addProperty("slot",slot);row.addProperty("item",item.save(server.registryAccess()).toString());SAVED.add(row);
    }
    private static void startArray(MinecraftServer server){
        var level=server.overworld();
        for(var cell:ForgingArrayRules.get().cells()){
            var pos=ForgingArrayPattern.worldPos(ARRAY,Direction.NORTH,cell);level.getChunkAt(pos);
            var state=cell.role()==ForgingArrayRules.Role.AIR?Blocks.AIR.defaultBlockState():
                    ForgingArrayContent.PARTS.get(cell.role()).get().defaultBlockState().setValue(ForgingArrayBlock.FACING,Direction.NORTH);
            level.setBlockAndUpdate(pos,state);
        }
        var controller=(ForgingArrayBlockEntity)level.getBlockEntity(ARRAY);
        var operator=player(server,"ForgeArrayLow",0);operator.setPos(10.5,80,9.5);
        var source=WorkshopEquipment.complete(new Workpiece("qingfeng_sword",4,WorkshopRules.get().forming_strikes(),1),4);
        controller.setItem(0,source);
        var prepared=EquipmentInfusion.prepare(source,"channeling",1);
        for(var cost:prepared.costs().entrySet()){
            var id=cost.getKey();
            var item=id.startsWith("#")?BuiltInRegistries.ITEM.getTagOrEmpty(TagKey.create(Registries.ITEM,ResourceLocation.parse(id.substring(1)))).iterator().next().value():
                    BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
            operator.getInventory().add(new ItemStack(item,cost.getValue()));
        }
        var menu=new ForgingArrayMenu(40,operator.getInventory(),controller,controller.data);operator.containerMenu=menu;
        check("array_select",menu.clickMenuButton(operator,10+ForgeMark.TRAITS.indexOf("channeling")),controller.status());
        check("array_started",menu.clickMenuButton(operator,0),controller.status());
        check("array_frozen_item_quality",controller.active()&&controller.quality()==4,controller.quality());
        var corrupted=controller.saveWithoutMetadata(server.registryAccess());corrupted.putInt("Quality",1);
        var rejected=new ForgingArrayBlockEntity(ARRAY,controller.getBlockState());rejected.loadWithComponents(corrupted,server.registryAccess());
        check("array_quality_mismatch_rejected",!rejected.active()&&rejected.locked()&&rejected.status()==11,rejected.status());
        menu.removed(operator);operator.containerMenu=operator.inventoryMenu;
    }
    private static void restart(MinecraftServer server)throws Exception{
        var chest=(ChestBlockEntity)server.overworld().getBlockEntity(CHEST);
        check("saved_chest_loaded",chest!=null,CHEST);
        for(var row:JsonParser.parseString(Files.readString(OUT.resolve("restart-fixture.json"))).getAsJsonArray()){
            var data=row.getAsJsonObject();int slot=data.get("slot").getAsInt();
            var expected=ItemStack.parseOptional(server.registryAccess(),TagParser.parseTag(data.get("item").getAsString()));
            var actual=chest.getItem(slot);
            check("saved_item_"+slot,ItemStack.matches(expected,actual),actual);DONE.add("saved_item_"+slot);
        }
        var level=server.overworld();level.getChunkAt(ARRAY);
        var controller=(ForgingArrayBlockEntity)level.getBlockEntity(ARRAY);
        check("array_loaded",controller!=null&&controller.quality()==4&&controller.active(),controller==null?"missing":controller.status());
        for(int i=0;i<ForgingArrayRules.get().duration()&&controller.active();i++)controller.serverTick();
        check("array_finished_after_restart",!controller.active()&&new ForgeMark("channeling",4).equals(controller.getItem(1).get(ModDataComponents.FORGE_MARK.get())),controller.getItem(1));
        DONE.add("array_resume_quality");
    }
    private static void finish(MinecraftServer server,Throwable error){
        finished=true;
        try{
            var selected=new ArrayList<String>();
            if(RESTART){for(int i=0;i<TRIALS.size()+1;i++)selected.add("saved_item_"+i);selected.add("array_resume_quality");}
            else{TRIALS.forEach(t->selected.add(t.id()));selected.addAll(List.of("crafting_event","infusion_quality","array_pending_quality","save_fixture"));}
            var unrun=new ArrayList<>(selected);unrun.removeAll(DONE);
            var result=new LinkedHashMap<String,Object>();result.put("phase",RESTART?"restart":"initial");result.put("passed",error==null&&unrun.isEmpty());
            result.put("selected",selected);result.put("completed",DONE);result.put("unrun",unrun);result.put("checks",CHECKS);
            result.put("method","native server menu and event fixtures plus actual world restart; no mapped player input");
            if(error!=null){error.printStackTrace();result.put("error",error.toString());}
            Files.writeString(OUT.resolve(RESTART?"restart.json":"initial.json"),JSON.toJson(result));
        }catch(Exception writeFailure){throw new RuntimeException(writeFailure);}
        server.halt(false);
    }
}
