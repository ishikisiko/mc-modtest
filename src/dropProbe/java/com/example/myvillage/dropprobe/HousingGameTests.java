package com.example.myvillage.dropprobe;

import com.example.myvillage.courtyard.*;
import com.example.myvillage.entity.ModEntities;
import com.example.myvillage.entity.npc.NpcEntity;
import com.example.myvillage.housing.HousingTemplates;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;

/** 独立探针中的六套住宅关卡，直接使用正式模板、导航和整门 Goal。 */
@GameTestHolder("yuanxian_drop_probe")
public final class HousingGameTests {
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    @GameTestGenerator public static Collection<TestFunction> houses(){
        var homes=HousingTemplates.all().stream().filter(i->i.role().equals("npc")).toList();
        String selection=System.getProperty("yuanxian.housing.lab.ids","");
        Set<String> ids=selection.isBlank()?homes.stream().map(HousingTemplates.Info::id).collect(java.util.stream.Collectors.toSet()):Set.of(selection.split(","));
        if(ids.isEmpty()||!homes.stream().map(HousingTemplates.Info::id).toList().containsAll(ids))throw new IllegalArgumentException("住宅测试名称无效");
        return homes.stream().filter(i->ids.contains(i.id())).map(info->new TestFunction("housing","housing."+info.id(),
            "yuanxian_drop_probe:housing_lab",HousingNavigationFixture.RULES.pathTimeoutTicks()*2+80,20,true,h->new Walk(h,info).start())).toList();
    }
    private static final class Walk {
        final GameTestHelper helper;final ServerLevel level;final HousingTemplates.Template home;final BlockPos root;
        final JsonObject report=new JsonObject();final List<BlockPos> doors=new ArrayList<>();
        NpcEntity npc;BlockPos outside,inside,goal;net.minecraft.world.level.pathfinder.Path lastPath;int phase=-1,returned,phaseStarted,attempts;boolean opened;
        Walk(GameTestHelper helper,HousingTemplates.Info info){this.helper=helper;level=helper.getLevel();home=HousingTemplates.get(info.id());root=helper.absolutePos(new BlockPos(4,4,6));report.addProperty("template",info.id());report.addProperty("source_hash",info.hash());report.addProperty("evidence","native_gametest");}
        void save(){try{Files.writeString(Path.of("housing-gametest-"+home.info().id()+".json"),JSON.toJson(report));}catch(java.io.IOException e){throw new IllegalStateException(e);}}
        void start(){
            try{
                CourtyardAssemblyBlock.beginEdit();try{
                    for(var unit:home.units())for(var cell:unit)level.setBlock(root.offset(cell.pos()),cell.state(),18);
                }finally{CourtyardAssemblyBlock.endEdit();}
                for(var e:home.blocks().entrySet())if(e.getValue().getBlock() instanceof CourtyardDoorBlock door&&door.part(e.getValue())==0)doors.add(root.offset(e.getKey()));
                outside=root.offset(home.info().entry().getX(),0,-2);
                for(int x=outside.getX()-2;x<=outside.getX()+2;x++)for(int z=root.getZ()-3;z<root.getZ();z++)level.setBlock(new BlockPos(x,root.getY()-1,z),Blocks.STONE_BRICKS.defaultBlockState(),18);
                var bed=home.blocks().entrySet().stream().filter(e->e.getValue().getBlock() instanceof BedBlock&&e.getValue().getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.BED_PART)==net.minecraft.world.level.block.state.properties.BedPart.HEAD).sorted(Map.Entry.comparingByKey()).findFirst().orElseThrow();
                BlockPos bedPos=root.offset(bed.getKey());inside=null;
                for(var dir:Direction.Plane.HORIZONTAL){var p=bedPos.relative(dir);if(level.isEmptyBlock(p)&&level.isEmptyBlock(p.above())&&!level.getBlockState(p.below()).getCollisionShape(level,p.below()).isEmpty()){inside=p;break;}}
                helper.assertTrue(inside!=null,"床边缺少真实站位");npc=ModEntities.CULTIVATOR.get().create(level);helper.assertTrue(npc!=null,"无法创建 NPC");npc.removeFreeWill();
                // 导航构造时就固定节点预算；正式居民扩大范围时也同步扩大此预算。
                npc.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(80);npc.configureBuildingNavigation();report.addProperty("search_range",npc.getAttributeValue(Attributes.FOLLOW_RANGE));
                npc.setPersistenceRequired();npc.setInvulnerable(true);npc.moveTo(outside.getX()+.5,outside.getY(),outside.getZ()+.5,0,0);helper.assertTrue(level.addFreshEntity(npc),"NPC 加入失败");
                report.addProperty("status","running");save();helper.onEachTick(this::tick);
            }catch(RuntimeException e){failed(e);throw e;}
        }
        void route(BlockPos target){
            goal=target;var path=HousingNavigationFixture.search(npc,target);lastPath=path;report.addProperty("path_attempts",++attempts);report.addProperty("route_started_on_ground",npc.onGround());report.add("target_"+phase,JSON.toJsonTree(new int[]{target.getX(),target.getY(),target.getZ()}));report.addProperty("path_exists_"+phase,path!=null);report.addProperty("path_reaches_"+phase,path!=null&&path.canReach());
            report.addProperty("target_path_type",net.minecraft.world.level.pathfinder.WalkNodeEvaluator.getPathTypeStatic(npc,target).toString());
            if(path!=null){report.addProperty("nodes_"+phase,path.getNodeCount());var nodes=new JsonArray();for(int i=0;i<path.getNodeCount();i++){var n=path.getNode(i);nodes.add(JSON.toJsonTree(new int[]{n.x,n.y,n.z}));}report.add("path_"+phase,nodes);}
            helper.assertTrue(HousingNavigationFixture.advances(npc,path),"正式导航无法继续前进 "+target.toShortString());helper.assertTrue(npc.getNavigation().moveTo(path,HousingNavigationFixture.RULES.walkSpeed()),"导航未启动");
        }
        void tick(){
            try{
                report.addProperty("ticks",helper.getTick());report.add("position",JSON.toJsonTree(new double[]{npc.getX(),npc.getY(),npc.getZ()}));report.addProperty("next_node",npc.getNavigation().getPath()==null?-1:npc.getNavigation().getPath().getNextNodeIndex());
                helper.assertTrue(npc.isAlive()&&!npc.isRemoved(),"NPC 中途消失");helper.assertTrue(level.noCollision(npc,npc.getBoundingBox().deflate(1e-5)),"NPC 身体穿入实体碰撞");
                if(phase<0){
                    if(npc.onGround()){phase=0;phaseStarted=(int)helper.getTick();report.addProperty("grounded_tick",helper.getTick());route(inside);}
                    else helper.assertTrue(helper.getTick()<60,"NPC 未能在测试入口自然落地");
                    return;
                }
                boolean anyOpen=doors.stream().anyMatch(p->level.getBlockState(p).getValue(CourtyardDoorBlock.OPEN));opened|=anyOpen;
                if(phase<2&&npc.onGround()&&HousingNavigationFixture.arrived(npc,phase==0?inside:outside)){
                    if(phase==0){report.addProperty("arrived_inside",true);phase=1;phaseStarted=(int)helper.getTick();route(outside);}
                    else{report.addProperty("arrived_outside",true);phase=2;returned=(int)helper.getTick();}
                }
                if(phase<2&&npc.onGround()&&npc.getNavigation().isDone()&&helper.getTick()%HousingNavigationFixture.RULES.pathRetryTicks()==0)route(phase==0?inside:outside);
                if(phase==2&&helper.getTick()-returned>=60){helper.assertTrue(opened,"途中未观察到整门自动开启");helper.assertTrue(!anyOpen,"返回门外后整门未关闭");report.addProperty("door_opened_and_closed",true);report.addProperty("status","passed");save();npc.discard();helper.succeed();return;}
                if(phase<2&&helper.getTick()-phaseStarted>=HousingNavigationFixture.RULES.pathTimeoutTicks())helper.fail("NPC 实际行走超时");if(helper.getTick()%20==0)save();
            }catch(RuntimeException e){failed(e);throw e;}
        }
        void failed(RuntimeException e){
            report.addProperty("status","failed");report.addProperty("error",e.toString());
            if(npc!=null){
                report.add("last_position",JSON.toJsonTree(new double[]{npc.getX(),npc.getY(),npc.getZ()}));report.add("nearby_collision",collisions(npc.blockPosition()));
                if(goal!=null)report.add("target_collision",collisions(goal));
                if(lastPath!=null&&lastPath.getEndNode()!=null){var end=lastPath.getEndNode().asBlockPos();report.add("end_node",JSON.toJsonTree(new int[]{end.getX(),end.getY(),end.getZ()}));report.add("end_collision",collisions(end));}
                npc.discard();
            }save();
        }
        JsonArray collisions(BlockPos center){
            var nearby=new JsonArray();
            for(var p:BlockPos.betweenClosed(center.offset(-2,-1,-2),center.offset(2,2,2))){
                var state=level.getBlockState(p);var shape=state.getCollisionShape(level,p);if(shape.isEmpty())continue;
                var row=new JsonObject();row.add("position",JSON.toJsonTree(new int[]{p.getX(),p.getY(),p.getZ()}));row.addProperty("state",state.toString());row.add("boxes",JSON.toJsonTree(shape.toAabbs()));nearby.add(row);
            }return nearby;
        }
    }
}
