package com.example.myvillage.dropprobe;

import com.example.myvillage.entity.ModEntities;
import com.example.myvillage.entity.npc.NpcEntity;
import com.example.myvillage.sim.runtime.WorldSimRuntime;
import com.example.myvillage.sim.runtime.life.*;
import com.example.myvillage.worldplan.capital.*;
import com.example.myvillage.worldplan.runtime.PlannedChunkGenerator;
import com.google.gson.*;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.minecraft.commands.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** 非命簿 NPC 使用正式导航与整门 Goal；到达只认可实际逐 tick 位移。 */
final class NpcHousingProbe {
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private static NpcEntity actor;private static BlockPos outside,inside;private static int index,age,phase;
    private static JsonObject walk;
    static void register(){NeoForge.EVENT_BUS.addListener(NpcHousingProbe::commands);NeoForge.EVENT_BUS.addListener(NpcHousingProbe::tick);}
    private static void commands(RegisterCommandsEvent e){
        var root=Commands.literal("npc_housing_probe").requires(s->s.hasPermission(4));
        for(String action:List.of("info","audit","staffed_audit","walk","enclosure"))root.then(Commands.literal(action).then(Commands.argument("index",IntegerArgumentType.integer(0,6))
            .executes(c->run(c.getSource(),action,IntegerArgumentType.getInteger(c,"index")))));
        e.getDispatcher().register(root);
    }
    private static List<CapitalCity> cities(ServerLevel l){return((PlannedChunkGenerator)l.getChunkSource().getGenerator()).plan().cities().stream().filter(c->!c.bundle().npcHouseholds.isEmpty()).sorted(Comparator.comparing(CapitalCity::cityId)).toList();}
    private static BlockPos pos(JsonArray a){return new BlockPos(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt());}
    private static JsonObject manifest(CapitalCity city){return JsonParser.parseString(new String(CapitalGameBundle.readResource(city.bundle().resource,8*1024*1024),StandardCharsets.UTF_8)).getAsJsonObject();}
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
    private static void save(String name,JsonObject report){try{Files.writeString(Path.of("npc-housing-probe-"+name+".json"),JSON.toJson(report));}catch(java.io.IOException e){throw new IllegalStateException(e);}}
    private static int run(CommandSourceStack source,String action,int i){
        JsonObject report=new JsonObject();
        try{
            ServerLevel l=source.getServer().overworld();var city=cities(l).get(i);var data=manifest(city);var home=data.getAsJsonArray("npc_housing").get(0).getAsJsonObject();
            var origin=new BlockPos(city.x(),city.originY(),city.z());var pair=city.bundle().npcHouseholds.getFirst();JsonObject rest=null,social=null;
            for(var element:city.bundle().life().getAsJsonArray("places")){var p=element.getAsJsonObject();if(p.get("id").getAsString().equals(pair.home()))rest=p;if(p.get("id").getAsString().equals(pair.work()))social=p;}
            require(rest!=null&&social!=null,"院宅生活站点不完整");
            report.addProperty("city",city.cityId());report.add("housing",home.deepCopy());
            report.add("origin",JSON.toJsonTree(new int[]{origin.getX(),origin.getY(),origin.getZ()}));
            report.add("enclosure",data.getAsJsonObject("qianji").getAsJsonArray("bounds"));
            report.addProperty("home_count",data.getAsJsonArray("npc_housing").size());
            BlockPos outsidePos=origin.offset(pos(social.getAsJsonArray("pos"))),insidePos=origin.offset(pos(rest.getAsJsonArray("pos")));
            report.add("outside",JSON.toJsonTree(new int[]{outsidePos.getX(),outsidePos.getY(),outsidePos.getZ()}));
            report.add("inside",JSON.toJsonTree(new int[]{insidePos.getX(),insidePos.getY(),insidePos.getZ()}));
            if(action.equals("audit")||action.equals("staffed_audit")){
                var building=data.getAsJsonArray("buildings").asList().stream().map(JsonElement::getAsJsonObject)
                        .filter(b->b.get("id").getAsString().equals(home.get("id").getAsString())).findFirst().orElseThrow();
                var bounds=building.getAsJsonObject("bounds");
                checkTiles(l,city,origin,pos(bounds.getAsJsonArray("min")),pos(bounds.getAsJsonArray("max")),report);
                var sim=WorldSimRuntime.sim().orElseThrow();
                var site=sim.lifeSite(city.cityId()).orElseThrow();
                var homeState=NpcLifeSites.availability(l,site.place(pair.home()).orElseThrow());
                var workState=NpcLifeSites.availability(l,site.place(pair.work()).orElseThrow());
                report.addProperty("bed_approach",homeState.id());
                report.addProperty("street_approach",workState.id());
                require(homeState==NpcLifeSites.Availability.AVAILABLE&&workState==NpcLifeSites.Availability.AVAILABLE,"床边或街边站位不通");
                var residents=sim.lifeResidents();
                var here=residents.stream().filter(r->r.siteId().equals(site.id())).toList();
                var household=here.stream().filter(r->r.homeId().equals(pair.home())).toList();
                var residentIds=residents.stream().map(LifeResidentView::personId).collect(java.util.stream.Collectors.toSet());
                long eligible=sim.livingIn(site.regionId()).stream()
                        .filter(p->p.sectId()<0&&!Set.of("travelling","secluded").contains(p.status()))
                        .filter(p->!residentIds.contains(p.id())&&!sim.combatLeased(p.id()))
                        .filter(p->sim.combatProfile(p.id()).map(profile->profile.body()==null||!profile.body().unresolved()).orElse(true))
                        .count();
                report.addProperty("unassigned_eligible_locals",eligible);
                report.addProperty("assigned_residents",household.size());
                report.add("household_person_ids",JSON.toJsonTree(household.stream().map(LifeResidentView::personId).toList()));
                require(!household.isEmpty()||(!action.equals("staffed_audit")&&(eligible==0||here.size()>=sim.lifeRules().maxResidentsPerSite())),
                        "符合入住条件时院宅仍无居民");
                require(household.stream().allMatch(r->r.workId().equals(pair.work())),"院宅居民未绑定生成的街边站位");
                require(residents.stream().noneMatch(r->r.homeId().startsWith("player_land")),"玩家宅地被分配给 NPC");
            }else if(action.equals("enclosure")){
                var office=data.getAsJsonObject("qianji");var bounds=office.getAsJsonArray("bounds");
                int base=office.get("base_y").getAsInt();
                checkTiles(l,city,origin,new BlockPos(bounds.get(0).getAsInt(),base,bounds.get(1).getAsInt()),
                        new BlockPos(bounds.get(2).getAsInt()-1,city.bundle().height-1,bounds.get(3).getAsInt()-1),report);
                var board=city.bundle().qianji.board();
                var boardPos=origin.offset(board.x(),board.y(),board.z());
                require(l.getBlockState(boardPos).is(com.example.myvillage.block.ModBlocks.JIANGHU_BOARD.get()),"围院实际任务榜未生成");
                for(var element:office.getAsJsonArray("signs")){
                    var position=origin.offset(pos(element.getAsJsonObject().getAsJsonArray("position")));
                    require(l.getBlockEntity(position) instanceof net.minecraft.world.level.block.entity.SignBlockEntity,"新楼匾额未随切片生成");
                }
            }else if(action.equals("walk")){
                require(actor==null,"上一轮行走仍在进行");index=i;outside=outsidePos;inside=insidePos;
                actor=ModEntities.CULTIVATOR.get().create(l);require(actor!=null,"无法创建测试 NPC");
                actor.removeFreeWill();
                actor.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(64);actor.configureBuildingNavigation();actor.setPersistenceRequired();actor.setInvulnerable(true);
                actor.moveTo(outside.getX()+.5,outside.getY(),outside.getZ()+.5,0,0);require(l.addFreshEntity(actor),"测试 NPC 加入失败");
                walk=report.deepCopy();walk.addProperty("status","running");age=0;phase=-1;save("walk-"+i,walk);report=walk.deepCopy();
            }
            if(!report.has("status"))report.addProperty("status","passed");
        }catch(Exception e){report.addProperty("status","failed");report.addProperty("error",e.toString());if(action.equals("walk")&&actor!=null){actor.discard();actor=null;}}
        save(action+"-"+i,report);var finalReport=report;source.sendSuccess(()->Component.literal("NPC_HOUSING_PROBE "+new Gson().toJson(finalReport)),false);return report.get("status").getAsString().equals("failed")?0:1;
    }
    private static void checkTiles(ServerLevel level,CapitalCity city,BlockPos origin,BlockPos low,BlockPos high,JsonObject report) throws IOException {
        int inspected=0,mismatches=0;
        var registry=level.registryAccess().lookupOrThrow(Registries.BLOCK);
        var samples=new JsonArray();
        for(var tile:city.bundle().tiles){
            if(tile.x()+15<low.getX()||tile.x()>high.getX()||tile.z()+15<low.getZ()||tile.z()>high.getZ())continue;
            var raw=CapitalGameBundle.readResource(CapitalGameBundle.templateFile(tile.template()),8*1024*1024);
            CapitalGameBundle.requireHash(raw,tile.sha256(),tile.template());
            var nbt=NbtIo.readCompressed(new ByteArrayInputStream(raw),NbtAccounter.unlimitedHeap());
            var palette=nbt.getList("palette",Tag.TAG_COMPOUND);
            var cells=nbt.getList("blocks",Tag.TAG_COMPOUND);
            for(int i=0;i<cells.size();i++){
                var row=cells.getCompound(i);var p=row.getList("pos",Tag.TAG_INT);
                int x=tile.x()+p.getInt(0),y=p.getInt(1),z=tile.z()+p.getInt(2);
                if(x<low.getX()||x>high.getX()||z<low.getZ()||z>high.getZ()||y<low.getY()||y>high.getY())continue;
                var entry=palette.getCompound(row.getInt("state"));var id=entry.getString("Name");
                if(id.equals("minecraft:air")||id.equals("minecraft:water")||id.endsWith("_leaves")||id.equals("minecraft:short_grass"))continue;
                var world=origin.offset(x,y,z);
                var expected=NbtUtils.readBlockState(registry,entry);
                if(!level.getBlockState(world).is(expected.getBlock())){
                    mismatches++;
                    if(samples.size()<12)samples.add(world.toShortString()+" "+id+" => "+level.getBlockState(world));
                }
                inspected++;
            }
        }
        report.addProperty("formal_blocks_inspected",inspected);report.addProperty("mismatches",mismatches);report.add("examples",samples);
        require(inspected>100&&mismatches==0,"新区块的静态实体方块与正式切片不符");
    }
    private static void tick(ServerTickEvent.Post event){
        if(actor==null)return;
        try{
            age++;require(actor.isAlive()&&!actor.isRemoved(),"测试 NPC 中途消失");require(age<2400,"院宅实际行走超时");
            if(phase<0){
                if(actor.onGround()){
                    var path=HousingNavigationFixture.search(actor,inside);require(HousingNavigationFixture.advances(actor,path),"院宅床边导航无法前进");require(actor.getNavigation().moveTo(path,HousingNavigationFixture.RULES.walkSpeed()),"院宅路径启动失败");
                    walk.addProperty("grounded_tick",age);walk.addProperty("nodes_in",path.getNodeCount());phase=0;
                }else require(age<60,"测试 NPC 未能在入口自然落地");
                return;
            }
            BlockPos target=phase==0?inside:outside;walk.addProperty("ticks",age);walk.add("position",JSON.toJsonTree(new double[]{actor.getX(),actor.getY(),actor.getZ()}));
            if(actor.onGround()&&HousingNavigationFixture.arrived(actor,target)){
                if(phase==0){walk.addProperty("arrived_inside",true);var path=HousingNavigationFixture.search(actor,outside);require(HousingNavigationFixture.advances(actor,path),"院宅出门导航无法前进");require(actor.getNavigation().moveTo(path,HousingNavigationFixture.RULES.walkSpeed()),"院宅出门启动失败");walk.addProperty("nodes_out",path.getNodeCount());phase=1;}
                else{walk.addProperty("arrived_outside",true);walk.addProperty("status","passed");save("walk-"+index,walk);actor.discard();actor=null;return;}
            }
            if(actor.onGround()&&actor.getNavigation().isDone()&&age%HousingNavigationFixture.RULES.pathRetryTicks()==0){
                var path=HousingNavigationFixture.search(actor,phase==0?inside:outside);require(HousingNavigationFixture.advances(actor,path),"分段导航未能继续前进");require(actor.getNavigation().moveTo(path,HousingNavigationFixture.RULES.walkSpeed()),"分段导航启动失败");
            }
            if(age%20==0)save("walk-"+index,walk);
        }catch(Exception e){walk.addProperty("status","failed");walk.addProperty("error",e.toString());save("walk-"+index,walk);actor.discard();actor=null;}
    }
}
