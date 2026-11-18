package com.example.myvillage.dropprobe;

import com.example.myvillage.courtyard.*;
import com.google.gson.*;
import java.nio.file.*;
import java.util.List;
import net.minecraft.commands.*;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Command fixtures in an isolated test world; this is not mapped player input. */
final class DoorProbe {
    private static final Gson JSON=new Gson();
    private static final BlockPos ROOT=new BlockPos(8,200,8);
    static void register(){NeoForge.EVENT_BUS.addListener(DoorProbe::commands);}
    private static void commands(RegisterCommandsEvent event){
        event.getDispatcher().register(Commands.literal("door_probe").requires(s->s.hasPermission(4))
            .then(Commands.literal("save").executes(c->run(c.getSource(),false)))
            .then(Commands.literal("restore").executes(c->run(c.getSource(),true)))
            .then(Commands.literal("homes").executes(c->homes(c.getSource()))));
    }
    private static void check(JsonArray rows,String name,boolean passed){
        var row=new JsonObject();row.addProperty("name",name);row.addProperty("passed",passed);rows.add(row);
        if(!passed)throw new IllegalStateException(name);
    }
    private static long drops(ServerLevel level,CourtyardDoorBlock door){
        return level.getEntitiesOfClass(ItemEntity.class,new AABB(-2,196,-2,18,209,18)).stream()
            .filter(e->e.getItem().is(door.asItem())).mapToLong(e->e.getItem().getCount()).sum();
    }
    private static void allStates(JsonArray rows,ServerLevel level,CourtyardDoorBlock door,Direction facing,boolean open){
        var v=door.family().variant(0);
        for(int i=0;i<v.partCount();i++){
            var pos=ROOT.offset(v.offset(i,facing));var state=level.getBlockState(pos);
            check(rows,"whole_"+facing+"_"+open+"_"+i,state.is(door)&&door.part(state)==i&&state.getValue(CourtyardDoorBlock.OPEN)==open&&door.root(pos,state).equals(ROOT));
        }
    }
    private static void clear(ServerLevel level,CourtyardDoorBlock door){
        for(var e:level.getEntitiesOfClass(ItemEntity.class,new AABB(-2,196,-2,18,209,18)))e.discard();
        CourtyardAssemblyBlock.beginEdit();
        try{for(int x=-2;x<18;x++)for(int z=-2;z<18;z++)for(int y=199;y<207;y++)
            level.setBlockAndUpdate(new BlockPos(x,y,z),y==199?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState());}
        finally{CourtyardAssemblyBlock.endEdit();}
    }
    private static int homes(CommandSourceStack source){
        var result=new JsonObject();var rows=new JsonArray();result.add("checks",rows);
        try{
            var level=source.getServer().overworld();var input=JsonParser.parseString(Files.readString(Path.of("home-probe.json"))).getAsJsonArray();
            for(var entry:input){
                var home=entry.getAsJsonObject();var origin=home.getAsJsonArray("origin");int mismatches=0;
                var min=new BlockPos(origin.get(0).getAsInt(),origin.get(1).getAsInt(),origin.get(2).getAsInt());
                for(var element:home.getAsJsonArray("blocks")){
                    var b=element.getAsJsonObject();var p=b.getAsJsonArray("pos");var pos=min.offset(p.get(0).getAsInt(),p.get(1).getAsInt(),p.get(2).getAsInt());
                    level.getChunkAt(pos);
                    var expected=net.minecraft.commands.arguments.blocks.BlockStateParser.parseForBlock(net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(),b.get("state").getAsString(),false).blockState();
                    if(!level.getBlockState(pos).equals(expected))mismatches++;
                }
                check(rows,"exact_template_readback_"+home.get("id").getAsString(),mismatches==0);
            }
            result.addProperty("status","passed");
        }catch(Exception e){result.addProperty("status","failed");result.addProperty("error",e.toString());}
        try{Files.writeString(Path.of("door-probe-homes.json"),JSON.toJson(result));}catch(java.io.IOException e){throw new IllegalStateException(e);}
        source.sendSuccess(()->Component.literal("DOOR_PROBE "+result),false);return result.get("status").getAsString().equals("passed")?1:0;
    }
    private static int run(CommandSourceStack source,boolean restore){
        var rows=new JsonArray();var result=new JsonObject();result.add("checks",rows);
        try{
            var level=source.getServer().overworld();
            for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){level.setChunkForced(x,z,true);level.getChunk(x,z);}
            var door=(CourtyardDoorBlock)CourtyardContent.BLOCK_ITEMS.get("courtyard_double_door").get();
            var item=CourtyardContent.ITEMS_BY_FAMILY.get("courtyard_double_door").get();var v=door.family().variant(0);
            var player=FakePlayerFactory.getMinecraft(level);player.setGameMode(GameType.SURVIVAL);player.moveTo(0,201,0,0,0);
            if(restore){
                allStates(rows,level,door,Direction.NORTH,true);
                check(rows,"restart_real_collision_open",level.noCollision(new AABB(9.2,200.15,7.8,11.8,203.9,10.8)));
                var part=ROOT.offset(4,2,0);var state=level.getBlockState(part);
                check(rows,"restart_right_click_closes",state.useWithoutItem(level,player,new BlockHitResult(Vec3.atCenterOf(part),Direction.NORTH,part,false)).consumesAction());
                allStates(rows,level,door,Direction.NORTH,false);
            }else{
                check(rows,"real_registered_door_and_item",item.assembly()==door&&v.partCount()==75&&door.variantProperty()!=null&&door.family().variants().size()==3);
                for(var facing:List.of(Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST)){
                    clear(level,door);player.setYRot(facing.getOpposite().toYRot());
                    var stack=new ItemStack(item,3);player.setItemInHand(InteractionHand.MAIN_HAND,stack);
                    var context=new BlockPlaceContext(player,InteractionHand.MAIN_HAND,stack,new BlockHitResult(Vec3.atCenterOf(ROOT.below()),Direction.UP,ROOT.below(),false));
                    var blocked=ROOT.offset(v.offset(74,facing));level.setBlockAndUpdate(blocked,Blocks.STONE.defaultBlockState());
                    check(rows,"occupied_swing_rejects_whole_placement_"+facing,!item.place(context).consumesAction()&&stack.getCount()==3&&level.getBlockState(ROOT).isAir());
                    level.setBlockAndUpdate(blocked,Blocks.AIR.defaultBlockState());
                    check(rows,"place_one_item_"+facing,item.place(context).consumesAction()&&stack.getCount()==2);
                    allStates(rows,level,door,facing,false);
                    var clicked=ROOT.offset(v.offset(34,facing));var hit=new BlockHitResult(Vec3.atCenterOf(clicked),facing,clicked,false);
                    var inconsistent=ROOT.offset(v.offset(74,facing));var original=level.getBlockState(inconsistent);
                    level.setBlockAndUpdate(inconsistent,original.setValue(CourtyardDoorBlock.OPEN,true));
                    check(rows,"inconsistent_whole_state_rejected_"+facing,!level.getBlockState(clicked).useWithoutItem(level,player,hit).consumesAction()&&!level.getBlockState(ROOT).getValue(CourtyardDoorBlock.OPEN));
                    level.setBlockAndUpdate(inconsistent,original);
                    check(rows,"right_click_non_root_opens_"+facing,level.getBlockState(clicked).useWithoutItem(level,player,hit).consumesAction());
                    allStates(rows,level,door,facing,true);
                    if(facing==Direction.NORTH){
                        check(rows,"native_open_portal_clear",level.noCollision(new AABB(9.2,200.15,7.8,11.8,203.9,10.8)));
                        var cow=EntityType.COW.create(level);cow.moveTo(10.5,200.125,8.7,0,0);cow.setNoAi(true);level.addFreshEntity(cow);
                        check(rows,"closing_on_entity_rejected",!level.getBlockState(clicked).useWithoutItem(level,player,hit).consumesAction());
                        allStates(rows,level,door,facing,true);cow.discard();
                    }
                    check(rows,"right_click_closes_"+facing,level.getBlockState(clicked).useWithoutItem(level,player,hit).consumesAction());
                    allStates(rows,level,door,facing,false);
                    if(facing==Direction.NORTH)check(rows,"native_closed_portal_blocks",!level.noCollision(new AABB(9.2,200.15,7.8,11.8,203.9,10.8)));
                    check(rows,"break_non_root_"+facing,level.destroyBlock(clicked,true,player));
                    check(rows,"one_whole_item_drop_"+facing,drops(level,door)==1);
                    for(int i=0;i<v.partCount();i++)check(rows,"removed_"+facing+"_"+i,!level.getBlockState(ROOT.offset(v.offset(i,facing))).is(door));
                }
                clear(level,door);player.setGameMode(GameType.CREATIVE);player.setYRot(0);
                for(int i=0;i<v.partCount();i++)level.setBlockAndUpdate(ROOT.offset(v.offset(i,Direction.NORTH)),door.state(0,i,Direction.NORTH,false));
                var clicked=ROOT.offset(4,2,0);var state=level.getBlockState(clicked);state.getBlock().playerWillDestroy(level,clicked,state,player);level.destroyBlock(clicked,false,player);
                check(rows,"creative_no_drop",drops(level,door)==0);
                for(int i=0;i<v.partCount();i++)check(rows,"creative_removed_"+i,!level.getBlockState(ROOT.offset(v.offset(i,Direction.NORTH))).is(door));
                for(int i=0;i<v.partCount();i++)level.setBlockAndUpdate(ROOT.offset(v.offset(i,Direction.NORTH)),door.state(0,i,Direction.NORTH,i==0));
                player.setGameMode(GameType.SURVIVAL);
                check(rows,"open_for_restart",door.setOpen(level,ROOT,level.getBlockState(ROOT),player,true));
                check(rows,"waterlogged_preserved",level.getBlockState(ROOT).getValue(CourtyardAssemblyBlock.WATERLOGGED));
            }
            result.addProperty("status","passed");
        }catch(Exception e){result.addProperty("status","failed");result.addProperty("error",e.toString());}
        try{Files.writeString(Path.of("door-probe-"+(restore?"restore":"save")+".json"),JSON.toJson(result));}
        catch(java.io.IOException e){throw new IllegalStateException(e);}
        var summary=new JsonObject();summary.addProperty("status",result.get("status").getAsString());summary.addProperty("checks",rows.size());if(result.has("error"))summary.add("error",result.get("error"));
        source.sendSuccess(()->Component.literal("DOOR_PROBE "+summary),false);return result.get("status").getAsString().equals("passed")?1:0;
    }
}
