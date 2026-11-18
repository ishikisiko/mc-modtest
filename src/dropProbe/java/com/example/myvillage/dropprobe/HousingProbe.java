package com.example.myvillage.dropprobe;

import com.example.myvillage.courtyard.CourtyardAssemblyBlock;
import com.example.myvillage.entity.npc.NpcEntity;
import com.example.myvillage.housing.*;
import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.*;
import net.minecraft.commands.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.*;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** 隔离新世界中的命令夹具；不作为客户端映射输入或玩家外观验收。 */
final class HousingProbe {
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private static final UUID OWNER=UUID.nameUUIDFromBytes("yuanxian_housing_probe_owner".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    private static FakePlayer player;private static BlockPos obstruction,farm;private static BlockState farmState;
    static void register(){NeoForge.EVENT_BUS.addListener(HousingProbe::commands);}
    private static void commands(RegisterCommandsEvent e){
        var root=Commands.literal("housing_probe").requires(s->s.hasPermission(4));
        for(String action:List.of("info","coverage","fees","blocked","blocked_check","unpaid","unpaid_check","resume","vacate","begin","status","restore","readback","alter","alter_check","cleanup","offline"))
            root.then(Commands.literal(action).executes(c->run(c.getSource(),action,0))
                .then(Commands.argument("index",IntegerArgumentType.integer(0,11)).executes(c->run(c.getSource(),action,IntegerArgumentType.getInteger(c,"index")))));
        root.then(Commands.literal("template").then(Commands.argument("index",IntegerArgumentType.integer(0,11)).executes(c->template(c.getSource(),IntegerArgumentType.getInteger(c,"index")))));
        e.getDispatcher().register(root);
    }
    private static List<HousingRuntime.Site> sites(ServerLevel level){return HousingRuntime.sites(level).stream().sorted(Comparator.comparing(HousingRuntime.Site::key)).toList();}
    private static void check(JsonArray rows,String name,boolean passed){
        var row=new JsonObject();row.addProperty("name",name);row.addProperty("passed",passed);rows.add(row);if(!passed)throw new IllegalStateException(name);
    }
    private static Item currency(){return BuiltInRegistries.ITEM.get(ResourceLocation.parse("yuanxian:low_grade_spirit_stone"));}
    private static int coins(){int count=0;for(int i=0;i<player.getInventory().getContainerSize();i++){var stack=player.getInventory().getItem(i);if(stack.is(currency()))count+=stack.getCount();}return count;}
    private static void cash(int amount){player.getInventory().clearContent();if(amount>0)player.getInventory().setItem(0,new ItemStack(currency(),amount));}
    @SuppressWarnings("unchecked") private static void online(ServerLevel level){
        player=FakePlayerFactory.get(level,new GameProfile(OWNER,"housing_owner"));player.setGameMode(GameType.SURVIVAL);
        try{
            Field field=PlayerList.class.getDeclaredField("playersByUUID");field.setAccessible(true);var map=(Map<UUID,ServerPlayer>)field.get(level.getServer().getPlayerList());map.put(OWNER,player);
        }catch(ReflectiveOperationException ex){throw new IllegalStateException(ex);}
    }
    private static HousingRuntime.Site select(ServerLevel level,int index){
        if(player==null)online(level);var site=sites(level).get(index);var at=site.world(site.plot().entry());
        if(at.distSqr(site.world(site.plot().steward()))>100)throw new IllegalStateException("夹具入口距离执事过远");
        player.setPos(at.getX()+.5,at.getY(),at.getZ()+.5);return site;
    }
    @SuppressWarnings("unchecked") private static UUID offer(HousingRuntime.Site site){
        var npc=player.serverLevel().getEntity(site.stewardId());
        if(!(npc instanceof NpcEntity n)||!HousingRuntime.open(player,n)){
            var at=site.world(site.plot().steward());
            throw new IllegalStateException("宅地执事尚未就绪：id="+site.stewardId()+", anchor="+at+", ticking="+player.serverLevel().isPositionEntityTicking(at)
                +", entity="+npc+", ledger="+(npc instanceof NpcEntity actor&&actor.isLedgerAvatar())+", distance="+(npc==null?-1:npc.distanceToSqr(net.minecraft.world.phys.Vec3.atBottomCenterOf(at)))
                +", support="+player.serverLevel().getBlockState(at.below())+", player="+player.position());
        }
        try{
            Field field=HousingRuntime.class.getDeclaredField("SESSIONS");field.setAccessible(true);var sessions=(Map<UUID,?>)field.get(null);var session=sessions.get(OWNER);
            var method=session.getClass().getDeclaredMethod("nonce");method.setAccessible(true);return(UUID)method.invoke(session);
        }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
    private static HousingIntent intent(HousingRuntime.Site site,int index){var h=HousingTemplates.players().get(index);return new HousingIntent(offer(site),false,h.id(),0,3,3);}
    private static boolean pending(String key)throws ReflectiveOperationException{var f=HousingRuntime.class.getDeclaredField("JOBS");f.setAccessible(true);return((Map<?,?>)f.get(null)).containsKey(key);}
    private static String preflightReason(HousingRuntime.Site site,int index)throws ReflectiveOperationException{
        var title=new HousingSavedData.Title(OWNER,"housing_owner");title.house=HousingTemplates.players().get(index%6).id();title.offsetX=3;title.offsetZ=3;
        var type=Arrays.stream(HousingRuntime.class.getDeclaredClasses()).filter(c->c.getSimpleName().equals("Job")).findFirst().orElseThrow();
        var ctor=type.getDeclaredConstructors()[0];ctor.setAccessible(true);var job=ctor.newInstance(site,title,true,OWNER,0,"yuanxian:low_grade_spirit_stone",player.serverLevel().getGameTime());
        var method=HousingRuntime.class.getDeclaredMethod("preflight",ServerLevel.class,type,HousingTemplates.Template.class);method.setAccessible(true);
        return(String)method.invoke(null,player.serverLevel(),job,HousingTemplates.get(title.house));
    }
    private static int result(CommandSourceStack source,String action,JsonObject result){
        try{Files.writeString(Path.of("housing-probe-"+action+".json"),JSON.toJson(result));}catch(java.io.IOException e){throw new IllegalStateException(e);}
        source.sendSuccess(()->Component.literal("HOUSING_PROBE "+new Gson().toJson(result)),false);return result.has("error")?0:1;
    }
    private static int run(CommandSourceStack source,String action,int index){
        var result=new JsonObject();var checks=new JsonArray();result.add("checks",checks);var level=source.getServer().overworld();var data=HousingSavedData.get(source.getServer());
        try{
            if(action.equals("info")){
                var rows=new JsonArray();for(var site:sites(level)){
                    var row=new JsonObject();row.addProperty("id",site.key());row.add("steward",JSON.toJsonTree(new int[]{site.world(site.plot().steward()).getX(),site.world(site.plot().steward()).getY(),site.world(site.plot().steward()).getZ()}));
                    row.add("origin",JSON.toJsonTree(new int[]{site.origin().getX(),site.origin().getY(),site.origin().getZ()}));row.addProperty("width",site.plot().width());row.addProperty("depth",site.plot().depth());rows.add(row);
                }result.add("sites",rows);check(checks,"seven_plots",rows.size()==7);check(checks,"six_player_choices",HousingTemplates.players().size()==6);
                check(checks,"twelve_registry_templates",HousingTemplates.all().stream().allMatch(h->HousingTemplates.get(h.id()).blocks().size()>5000));
                check(checks,"development_free_defaults",HousingConfig.CLAIM_COST.get()==0&&HousingConfig.BUILD_COST.get()==0);
            }else if(action.equals("coverage")){
                for(var site:sites(level)){var title=data.titles.get(site.key());check(checks,"built_owned_"+site.key(),title!=null&&title.owner.equals(OWNER)&&title.status.equals("built"));}
            }else if(action.equals("cleanup")||action.equals("offline")){
                if(action.equals("cleanup")){HousingConfig.CLAIM_COST.set(0);HousingConfig.BUILD_COST.set(0);HousingConfig.BLOCKS_PER_TICK.set(1024);HousingConfig.SPEC.save();}
                if(player!=null){var f=PlayerList.class.getDeclaredField("playersByUUID");f.setAccessible(true);((Map<?,?>)f.get(source.getServer().getPlayerList())).remove(OWNER);player=null;}
            }else if(action.equals("status")){
                var row=data.titles.get(sites(level).get(index).key());if(row!=null){result.addProperty("state",row.status);result.addProperty("cursor",row.cursor);result.addProperty("house",row.house);result.addProperty("owner",row.owner.toString());result.addProperty("reason",row.error);}
                result.addProperty("pending",pending(sites(level).get(index).key()));
                result.addProperty("coins",player==null?-1:coins());
                if(player!=null&&row!=null&&row.status.equals("empty")&&!pending(sites(level).get(index).key()))result.addProperty("preflight_reason",preflightReason(sites(level).get(index),index));
            }else{
                var site=select(level,index);var title=data.titles.get(site.key());
                switch(action){
                    case "fees"->{
                        check(checks,"first_plot_empty",title==null);HousingConfig.CLAIM_COST.set(3);HousingConfig.BUILD_COST.set(7);cash(2);
                        HousingRuntime.choose(player,new HousingIntent(offer(site),true,"",0,0,0));check(checks,"failed_claim_keeps_currency",data.titles.get(site.key())==null&&coins()==2);
                        cash(12);var request=new HousingIntent(offer(site),true,"",0,0,0);HousingRuntime.choose(player,request);
                        check(checks,"claim_charged_once",data.titles.get(site.key()).owner.equals(OWNER)&&coins()==9);HousingRuntime.choose(player,request);check(checks,"claim_nonce_replay_no_charge",coins()==9);
                        var invalid=new HousingIntent(offer(site),false,HousingTemplates.players().getFirst().id(),0,Integer.MAX_VALUE,3);HousingRuntime.choose(player,invalid);
                        check(checks,"overflow_intent_keeps_empty_plot",data.titles.get(site.key()).status.equals("empty")&&coins()==9);
                        HousingConfig.CLAIM_COST.set(0);HousingConfig.SPEC.save();
                    }
                    case "blocked"->{
                        var t=HousingTemplates.get(HousingTemplates.players().getFirst().id());var occupied=t.blocks().entrySet().stream().filter(e->e.getKey().getY()>=0&&!e.getValue().isAir()).findFirst().orElseThrow().getKey();
                        obstruction=site.origin().offset(3,0,3).offset(occupied);farm=site.origin().offset(site.plot().width()-2,-1,site.plot().depth()-2);farmState=Blocks.FARMLAND.defaultBlockState().setValue(net.minecraft.world.level.block.FarmBlock.MOISTURE,7);
                        level.setBlockAndUpdate(obstruction,Blocks.GOLD_BLOCK.defaultBlockState());level.setBlockAndUpdate(farm,farmState);level.setBlockAndUpdate(farm.above(),Blocks.WHEAT.defaultBlockState().setValue(net.minecraft.world.level.block.CropBlock.AGE,7));cash(9);HousingRuntime.choose(player,intent(site,0));
                    }
                    case "blocked_check"->{
                        check(checks,"existing_build_refuses_without_debit",title!=null&&title.status.equals("empty")&&coins()==9);
                        String reason=preflightReason(site,index);check(checks,"existing_block_explains_veto",reason!=null&&reason.contains(obstruction.toShortString()));
                        check(checks,"existing_block_preserved",level.getBlockState(obstruction).is(Blocks.GOLD_BLOCK));check(checks,"outside_farmland_preserved",level.getBlockState(farm).is(Blocks.FARMLAND));level.setBlockAndUpdate(obstruction,Blocks.AIR.defaultBlockState());
                    }
                    case "unpaid"->{cash(2);HousingRuntime.choose(player,intent(site,0));}
                    case "unpaid_check"->{
                        check(checks,"insufficient_build_fee_keeps_empty_plot",title!=null&&title.status.equals("empty")&&coins()==2);
                        check(checks,"unpaid_site_has_no_other_veto",preflightReason(site,index)==null&&HousingConfig.BUILD_COST.get()==7);
                    }
                    case "resume"->{
                        check(checks,"resume_only_owned_empty_fixture",title!=null&&title.owner.equals(OWNER)&&title.status.equals("empty")&&title.house.isEmpty()&&title.cursor==0&&!pending(site.key()));
                        HousingConfig.BUILD_COST.set(7);HousingConfig.SPEC.save();
                    }
                    case "vacate"->{
                        // 新区块自然生成的动物先移到宅地外；保留其身份并记录实际位置，不改命簿投影。
                        var origin=site.origin();var moved=new JsonArray();result.add("fixture_animals",moved);
                        var box=new net.minecraft.world.phys.AABB(origin.getX(),origin.getY()-2,origin.getZ(),origin.getX()+site.plot().width(),origin.getY()+64,origin.getZ()+site.plot().depth()).inflate(1);
                        var template=HousingTemplates.players().get(index%6);var buildArea=new net.minecraft.world.phys.AABB(origin.getX()+3,origin.getY(),origin.getZ()+3,origin.getX()+3+template.width(),origin.getY()+template.height(),origin.getZ()+3+template.depth());
                        for(var entity:level.getEntities((net.minecraft.world.entity.Entity)null,box,e->e.isAlive()&&!e.isSpectator())){
                            if(!(entity instanceof net.minecraft.world.entity.animal.Animal)&&!(entity instanceof net.minecraft.world.entity.item.ItemEntity)){
                                if(entity.getBoundingBox().intersects(buildArea))throw new IllegalStateException("夹具不移动施工范围内的该实体："+entity);
                                continue;
                            }
                            BlockPos entry=site.world(site.plot().entry()),park=null;
                            search:for(int radius=2;radius<=16;radius++)for(int dz=-radius;dz<=radius;dz++)for(int dx=-radius;dx<=radius;dx++){
                                if(Math.max(Math.abs(dx),Math.abs(dz))!=radius)continue;
                                for(int dy=-2;dy<=2;dy++){
                                    var p=entry.offset(dx,dy,dz);
                                    if(p.distSqr(site.world(site.plot().steward()))<25||p.distSqr(entry)<16)continue;
                                    if(p.getX()>=origin.getX()-1&&p.getX()<origin.getX()+site.plot().width()+1&&p.getZ()>=origin.getZ()-1&&p.getZ()<origin.getZ()+site.plot().depth()+1)continue;
                                    if(!level.getBlockState(p.below()).isFaceSturdy(level,p.below(),net.minecraft.core.Direction.UP))continue;
                                    var body=entity.getBoundingBox().move(p.getX()+.5-entity.getX(),p.getY()-entity.getY(),p.getZ()+.5-entity.getZ());
                                    if(level.noCollision(entity,body)&&level.getEntities(entity,body,e->e.isAlive()).isEmpty()){park=p;break search;}
                                }
                            }
                            if(park==null)throw new IllegalStateException("动物没有宅地外的安全停留点："+entity);
                            var row=new JsonObject();row.addProperty("id",entity.getUUID().toString());row.addProperty("type",BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());row.add("from",JSON.toJsonTree(new double[]{entity.getX(),entity.getY(),entity.getZ()}));
                            entity.teleportTo(park.getX()+.5,park.getY(),park.getZ()+.5);
                            if(entity instanceof net.minecraft.world.entity.animal.Animal animal){row.addProperty("previous_no_ai",animal.isNoAi());animal.setNoAi(true);}
                            row.add("to",JSON.toJsonTree(new double[]{entity.getX(),entity.getY(),entity.getZ()}));moved.add(row);
                        }
                    }
                    case "begin"->{
                        if(title==null){HousingConfig.CLAIM_COST.set(0);HousingConfig.BUILD_COST.set(0);HousingRuntime.choose(player,new HousingIntent(offer(site),true,"",0,0,0));}
                        if(index==0){HousingConfig.BUILD_COST.set(7);HousingConfig.BLOCKS_PER_TICK.set(128);cash(9);}else{HousingConfig.BUILD_COST.set(0);HousingConfig.BLOCKS_PER_TICK.set(1024);}
                        HousingConfig.SPEC.save();var request=intent(site,index%6);HousingRuntime.choose(player,request);HousingRuntime.choose(player,request);
                        result.addProperty("alive",player.isAlive());result.addProperty("distance",player.distanceToSqr(net.minecraft.world.phys.Vec3.atBottomCenterOf(site.world(site.plot().steward()))));
                        check(checks,"build_request_started_once",pending(site.key()));
                    }
                    case "restore"->{cash(2);check(checks,"title_restored",title!=null&&title.owner.equals(OWNER)&&title.house.equals(HousingTemplates.players().getFirst().id()));result.addProperty("state",title.status);result.addProperty("cursor",title.cursor);}
                    case "readback"->{
                        check(checks,"construction_completed",title!=null&&title.status.equals("built"));var t=HousingTemplates.get(title.house);int mismatches=0;
                        for(var cell:t.blocks().entrySet()){
                            var p=site.origin().offset(title.offsetX,0,title.offsetZ).offset(HousingTemplates.rotate(cell.getKey(),t.info().width(),t.info().depth(),title.turn));
                            if(!level.getBlockState(p).equals(cell.getValue().rotate(HousingTemplates.rotation(title.turn))))mismatches++;
                        }check(checks,"all_template_cells_match",mismatches==0);result.addProperty("cells",t.blocks().size());
                        if(index==0)check(checks,"restart_did_not_charge_again",coins()==2);
                    }
                    case "alter","alter_check"->{
                        var t=HousingTemplates.get(title.house);var p=t.blocks().entrySet().stream().filter(e->e.getKey().getY()>=1&&e.getValue().getBlock()==Blocks.STONE_BRICKS).findFirst().orElseThrow().getKey();
                        var target=site.origin().offset(title.offsetX,0,title.offsetZ).offset(HousingTemplates.rotate(p,t.info().width(),t.info().depth(),title.turn));
                        if(action.equals("alter"))level.setBlockAndUpdate(target,Blocks.DIAMOND_BLOCK.defaultBlockState());
                        else check(checks,"finished_home_never_restamped",level.getBlockState(target).is(Blocks.DIAMOND_BLOCK));
                    }
                    default->throw new IllegalArgumentException(action);
                }
            }
            result.addProperty("status","passed");
        }catch(Exception e){result.addProperty("status","failed");result.addProperty("error",e.toString());var trace=new java.io.StringWriter();e.printStackTrace(new java.io.PrintWriter(trace));result.addProperty("traceback",trace.toString());}
        return result(source,action+"-"+index,result);
    }
    private static int template(CommandSourceStack source,int index){
        var report=new JsonObject();var checks=new JsonArray();report.add("checks",checks);var level=source.getServer().overworld();
        try{
            var t=HousingTemplates.get(HousingTemplates.all().get(index).id());var root=new BlockPos(1024+index*80,160,1024);int mismatches=0;
            CourtyardAssemblyBlock.beginEdit();try{
                for(var c:t.columns())for(int y=c.bottom();y<c.top();y++)level.setBlock(root.offset(c.x(),y,c.z()),Blocks.AIR.defaultBlockState(),18);
                for(var unit:t.units())for(var cell:unit)level.setBlock(root.offset(cell.pos()),cell.state(),18);
            }finally{CourtyardAssemblyBlock.endEdit();}
            for(var cell:t.blocks().entrySet())if(!level.getBlockState(root.offset(cell.getKey())).equals(cell.getValue()))mismatches++;
            check(checks,"exact_registry_template_"+t.info().id(),mismatches==0);report.addProperty("cells",t.blocks().size());report.addProperty("status","passed");
        }catch(Exception e){report.addProperty("status","failed");report.addProperty("error",e.toString());}
        return result(source,"template-"+index,report);
    }
}
